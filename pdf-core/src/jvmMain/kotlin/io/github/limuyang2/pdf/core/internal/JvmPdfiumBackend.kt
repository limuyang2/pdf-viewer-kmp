package io.github.limuyang2.pdf.core.internal

import com.sun.jna.Memory
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference
import io.github.limuyang2.pdf.core.PdfBitmap
import io.github.limuyang2.pdf.core.PdfBookmark
import io.github.limuyang2.pdf.core.PdfCapabilities
import io.github.limuyang2.pdf.core.PdfDocumentInfo
import io.github.limuyang2.pdf.core.PdfIncorrectPasswordException
import io.github.limuyang2.pdf.core.PdfInvalidFormatException
import io.github.limuyang2.pdf.core.PdfIoException
import io.github.limuyang2.pdf.core.PdfLink
import io.github.limuyang2.pdf.core.PdfLinkTarget
import io.github.limuyang2.pdf.core.PdfMetadata
import io.github.limuyang2.pdf.core.PdfNativeException
import io.github.limuyang2.pdf.core.PdfPageException
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPasswordRequiredException
import io.github.limuyang2.pdf.core.PdfPermissions
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfPoint
import io.github.limuyang2.pdf.core.PdfQuad
import io.github.limuyang2.pdf.core.PdfRect
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfRotation
import io.github.limuyang2.pdf.core.PdfSearchMatch
import io.github.limuyang2.pdf.core.PdfSearchOptions
import io.github.limuyang2.pdf.core.PdfSize
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfTextLayout
import io.github.limuyang2.pdf.core.PdfTextRange
import io.github.limuyang2.pdf.core.PdfUnsupportedFeatureException
import io.github.limuyang2.pdf.core.PdfUnsupportedSecurityException
import io.github.limuyang2.pdf.core.PdfVersion

internal object JvmPdfiumBackend : PdfiumBackend {
    private const val BITMAP_BGRA = 4
    private const val RENDER_ANNOTATIONS = 0x01
    private const val RENDER_LCD_TEXT = 0x02
    private const val RENDER_GRAYSCALE = 0x08

    private data class Document(
        val pointer: Pointer,
        val memory: Memory,
    )

    private var library: JvmPdfiumLibrary? = null
    private val documents = mutableMapOf<Long, Document>()

    override val capabilities: PdfCapabilities =
        PdfCapabilities(
            text = true,
            search = true,
            bookmarks = false,
            links = true,
            thumbnails = false,
            progressiveLoading = false,
            progressiveRendering = false,
            forms = false,
            editing = false,
            javascriptExecution = false,
            xfa = false,
        )

    override suspend fun initialize() {
        check(library == null) { "JVM PDFium is already initialized" }
        JvmPdfiumLibraryLoader.load().also {
            it.FPDF_InitLibrary()
            library = it
        }
    }

    override fun destroy() {
        check(documents.isEmpty()) {
            "Cannot destroy JVM PDFium while documents remain open"
        }
        library?.FPDF_DestroyLibrary()
        library = null
    }

    override fun open(
        source: PdfSource,
        password: String?,
    ): OpenedDocument {
        val bytes =
            when (source) {
                is PdfSource.Bytes -> source.data
                is PdfSource.RandomAccess ->
                    unsupported("random-access document sources on JVM")
            }
        if (bytes.isEmpty()) throw PdfInvalidFormatException()
        require(password?.contains('\u0000') != true) {
            "password must not contain a null character"
        }

        val memory = Memory(bytes.size.toLong())
        memory.write(0, bytes, 0, bytes.size)
        val pdfium = requireLibrary()
        val pointer =
            pdfium.FPDF_LoadMemDocument64(
                memory,
                NativeSize(bytes.size.toLong()),
                password,
            ) ?: throwOpenFailure(
                errorCode = pdfium.FPDF_GetLastError().toInt(),
                passwordWasSupplied = password != null,
            )
        val pageCount = pdfium.FPDF_GetPageCount(pointer)
        if (pageCount < 0) {
            pdfium.FPDF_CloseDocument(pointer)
            throw PdfNativeException(
                nativeErrorCode = 0,
                message = "PDFium returned an invalid page count: $pageCount",
            )
        }
        val handle = Pointer.nativeValue(pointer)
        check(documents.put(handle, Document(pointer, memory)) == null) {
            "PDFium returned a duplicate document handle"
        }
        return OpenedDocument(NativeDocumentHandle(handle), pageCount)
    }

    override fun close(document: NativeDocumentHandle) {
        val opened =
            documents.remove(document.value)
                ?: throw PdfNativeException(
                    nativeErrorCode = 0,
                    message = "Unknown JVM PDFium document handle",
                )
        requireLibrary().FPDF_CloseDocument(opened.pointer)
        opened.memory.close()
    }

    override fun documentInformation(
        document: NativeDocumentHandle,
    ): PdfDocumentInfo {
        val pdfium = requireLibrary()
        val pointer = document(document)
        val version = IntByReference()
        val hasVersion = pdfium.FPDF_GetFileVersion(pointer, version) != 0
        val encodedVersion = version.value
        val permissions = pdfium.FPDF_GetDocPermissions(pointer).toLong().toUInt()
        return PdfDocumentInfo(
            version =
                if (hasVersion) {
                    PdfVersion(
                        major = encodedVersion / 10,
                        minor = encodedVersion % 10,
                    )
                } else {
                    null
                },
            permissions = permissions.toPdfPermissions(),
            securityRevision =
                pdfium
                    .FPDF_GetSecurityHandlerRevision(pointer)
                    .takeIf { it >= 0 },
            hasValidCrossReferenceTable =
                pdfium.FPDF_DocumentHasValidCrossReferenceTable(pointer) != 0,
            isLinearized = null,
        )
    }

    override fun metadata(document: NativeDocumentHandle): PdfMetadata {
        fun value(tag: String): String? =
            readUtf16(emptyAsNull = true) { buffer, length ->
                requireLibrary().FPDF_GetMetaText(
                    document(document),
                    tag,
                    buffer,
                    NativeLong(length),
                )
            }
        return PdfMetadata(
            title = value("Title"),
            author = value("Author"),
            subject = value("Subject"),
            keywords = value("Keywords"),
            creator = value("Creator"),
            producer = value("Producer"),
            creationDate = value("CreationDate"),
            modificationDate = value("ModDate"),
        )
    }

    override fun pageLabel(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): String? =
        readUtf16(emptyAsNull = false) { buffer, length ->
            requireLibrary().FPDF_GetPageLabel(
                document(document),
                pageIndex,
                buffer,
                NativeLong(length),
            )
        }

    override fun pageInformation(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfPageInfo =
        withPage(document, pageIndex) { page ->
            val pdfium = requireLibrary()
            val bounds = FsRectF()
            val hasBounds = pdfium.FPDF_GetPageBoundingBox(page, bounds) != 0
            val rotation = pdfRotation(pdfium.FPDFPage_GetRotation(page))
            val nativeWidth = pdfium.FPDF_GetPageWidthF(page).toDouble()
            val nativeHeight = pdfium.FPDF_GetPageHeightF(page).toDouble()
            val swapsDimensions =
                rotation == PdfRotation.Degrees90 ||
                    rotation == PdfRotation.Degrees270
            PdfPageInfo(
                size =
                    PdfSize(
                        if (swapsDimensions) nativeHeight else nativeWidth,
                        if (swapsDimensions) nativeWidth else nativeHeight,
                    ),
                rotation = rotation,
                boundingBox =
                    if (hasBounds) {
                        PdfRect(
                            left = bounds.left.toDouble(),
                            bottom = bounds.bottom.toDouble(),
                            right = bounds.right.toDouble(),
                            top = bounds.top.toDouble(),
                        )
                    } else {
                        null
                    },
            )
        }

    override fun render(
        document: NativeDocumentHandle,
        pageIndex: Int,
        request: PdfRenderRequest,
    ): PdfBitmap {
        if (request.sourceRect != null) {
            unsupported("cropped page rendering on JVM")
        }
        val width = request.outputSize.width
        val height = request.outputSize.height
        val strideLong = width.toLong() * 4
        val byteCountLong = strideLong * height
        require(strideLong <= Int.MAX_VALUE && byteCountLong <= Int.MAX_VALUE) {
            "render output is too large"
        }
        val stride = strideLong.toInt()
        val memory = Memory(byteCountLong)
        return try {
            withPage(document, pageIndex) { page ->
                val pdfium = requireLibrary()
                val bitmap =
                    pdfium.FPDFBitmap_CreateEx(
                        width,
                        height,
                        BITMAP_BGRA,
                        memory,
                        stride,
                    ) ?: throw PdfNativeException(
                        nativeErrorCode = 0,
                        message = "PDFium could not allocate a page bitmap",
                    )
                try {
                    check(
                        pdfium.FPDFBitmap_FillRect(
                            bitmap,
                            0,
                            0,
                            width,
                            height,
                            request.backgroundColor.argb.toInt(),
                        ) != 0,
                    ) {
                        "PDFium could not initialize the page bitmap"
                    }
                    var flags = 0
                    if (request.renderAnnotations) {
                        flags = flags or RENDER_ANNOTATIONS
                    }
                    if (request.grayscale) flags = flags or RENDER_GRAYSCALE
                    if (request.optimizeTextForLcd) {
                        flags = flags or RENDER_LCD_TEXT
                    }
                    pdfium.FPDF_RenderPageBitmap(
                        bitmap,
                        page,
                        0,
                        0,
                        width,
                        height,
                        request.rotation.degrees / 90,
                        flags,
                    )
                } finally {
                    pdfium.FPDFBitmap_Destroy(bitmap)
                }
            }
            OwnedPdfBitmap(
                width = width,
                height = height,
                stride = stride,
                pixels = memory.getByteArray(0, byteCountLong.toInt()),
            )
        } finally {
            memory.close()
        }
    }

    override fun extractText(
        document: NativeDocumentHandle,
        pageIndex: Int,
        range: PdfTextRange?,
    ): String =
        withPage(document, pageIndex) { page ->
            val pdfium = requireLibrary()
            val textPage =
                pdfium.FPDFText_LoadPage(page)
                    ?: throw PdfPageException(pageIndex)
            try {
                val totalCount = pdfium.FPDFText_CountChars(textPage)
                if (totalCount < 0) throw PdfPageException(pageIndex)
                val start = range?.startCharacterIndex ?: 0
                val count = range?.characterCount ?: (totalCount - start)
                require(start <= totalCount && count <= totalCount - start) {
                    "text range exceeds the page character count"
                }
                val buffer = Memory((count.toLong() + 1) * 2)
                try {
                    val written =
                        pdfium.FPDFText_GetText(textPage, start, count, buffer)
                    check(written == count + 1) {
                        "PDFium returned an invalid text length: $written"
                    }
                    buffer
                        .getByteArray(0, count * 2)
                        .toString(Charsets.UTF_16LE)
                } finally {
                    buffer.close()
                }
            } finally {
                pdfium.FPDFText_ClosePage(textPage)
            }
        }

    override fun bookmarks(document: NativeDocumentHandle): List<PdfBookmark> =
        unsupported("bookmarks on JVM")

    override fun thumbnail(
        document: NativeDocumentHandle,
        pageIndex: Int,
        maximumSize: PdfPixelSize,
    ): PdfBitmap? = unsupported("embedded thumbnails on JVM")

    override fun textLayout(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfTextLayout = unsupported("text layout on JVM")

    override fun search(
        document: NativeDocumentHandle,
        pageIndex: Int,
        query: String,
        options: PdfSearchOptions,
    ): List<PdfSearchMatch> =
        withPage(document, pageIndex) { page ->
            val pdfium = requireLibrary()
            val textPage =
                pdfium.FPDFText_LoadPage(page)
                    ?: throw PdfPageException(pageIndex)
            try {
                val queryBytes = query.toByteArray(Charsets.UTF_16LE)
                val queryMemory = Memory(queryBytes.size.toLong() + 2)
                try {
                    queryMemory.write(0, queryBytes, 0, queryBytes.size)
                    queryMemory.setShort(queryBytes.size.toLong(), 0)
                    val search =
                        pdfium.FPDFText_FindStart(
                            textPage,
                            queryMemory,
                            NativeLong(jvmPdfiumSearchFlags(options).toLong()),
                            0,
                        ) ?: throw PdfPageException(pageIndex)
                    try {
                        buildList {
                            while (pdfium.FPDFText_FindNext(search) != 0) {
                                add(
                                    jvmPdfSearchMatch(
                                        pdfium,
                                        textPage,
                                        search,
                                        pageIndex,
                                    ),
                                )
                            }
                        }
                    } finally {
                        pdfium.FPDFText_FindClose(search)
                    }
                } finally {
                    queryMemory.close()
                }
            } finally {
                pdfium.FPDFText_ClosePage(textPage)
            }
        }

    override fun links(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): List<PdfLink> =
        withPage(document, pageIndex) { page ->
            val pdfium = requireLibrary()
            val nativeDocument = document(document)
            val position = IntByReference()
            val linkReference = PointerByReference()
            buildList {
                while (
                    pdfium.FPDFLink_Enumerate(
                        page,
                        position,
                        linkReference,
                    ) != 0
                ) {
                    val link =
                        linkReference.value
                            ?: throw PdfPageException(pageIndex)
                    add(jvmPdfLink(nativeDocument, link))
                }
            }
        }

    private fun jvmPdfLink(
        document: Pointer,
        link: Pointer,
    ): PdfLink {
        val pdfium = requireLibrary()
        val directDestination = pdfium.FPDFLink_GetDest(document, link)
        val action = pdfium.FPDFLink_GetAction(link)
        val actionType =
            action?.let { pdfium.FPDFAction_GetType(it).toInt() }
                ?: ACTION_UNSUPPORTED
        val target =
            when {
                directDestination != null ->
                    PdfLinkTarget.Internal(
                        jvmDestination(document, directDestination),
                    )
                actionType == ACTION_GOTO -> {
                    val destination =
                        checkNotNull(
                            pdfium.FPDFAction_GetDest(
                                document,
                                checkNotNull(action),
                            ),
                        ) {
                            "A PDF go-to action has no destination"
                        }
                    PdfLinkTarget.Internal(
                        jvmDestination(document, destination),
                    )
                }
                actionType == ACTION_URI -> {
                    val uri =
                        readUtf8 { buffer, length ->
                            pdfium.FPDFAction_GetURIPath(
                                document,
                                checkNotNull(action),
                                buffer,
                                NativeLong(length),
                            )
                        }
                    if (uri.isNullOrEmpty()) {
                        PdfLinkTarget.Unsupported(actionType)
                    } else {
                        PdfLinkTarget.Uri(uri)
                    }
                }
                actionType == ACTION_REMOTE_GOTO -> {
                    val filePath =
                        readUtf8 { buffer, length ->
                            pdfium.FPDFAction_GetFilePath(
                                checkNotNull(action),
                                buffer,
                                NativeLong(length),
                            )
                        }
                    PdfLinkTarget.RemoteDocument(filePath, null)
                }
                else -> PdfLinkTarget.Unsupported(actionType)
            }
        return PdfLink(
            bounds = jvmLinkBounds(link),
            target = target,
        )
    }

    private fun jvmPdfSearchMatch(
        pdfium: JvmPdfiumLibrary,
        textPage: Pointer,
        search: Pointer,
        pageIndex: Int,
    ): PdfSearchMatch {
        val startCharacterIndex =
            pdfium.FPDFText_GetSchResultIndex(search)
        val characterCount = pdfium.FPDFText_GetSchCount(search)
        if (startCharacterIndex < 0 || characterCount <= 0) {
            throw PdfPageException(pageIndex)
        }
        val rectCount =
            pdfium.FPDFText_CountRects(
                textPage,
                startCharacterIndex,
                characterCount,
            )
        if (rectCount < 0) throw PdfPageException(pageIndex)
        val bounds =
            List(rectCount) { rectIndex ->
                val left = DoubleByReference()
                val top = DoubleByReference()
                val right = DoubleByReference()
                val bottom = DoubleByReference()
                if (
                    pdfium.FPDFText_GetRect(
                        textPage,
                        rectIndex,
                        left,
                        top,
                        right,
                        bottom,
                    ) == 0
                ) {
                    throw PdfPageException(pageIndex)
                }
                PdfRect(
                    left = left.value,
                    bottom = bottom.value,
                    right = right.value,
                    top = top.value,
                )
            }
        return PdfSearchMatch(
            range =
                PdfTextRange(
                    startCharacterIndex = startCharacterIndex,
                    characterCount = characterCount,
                ),
            bounds = bounds,
        )
    }

    private fun jvmPdfiumSearchFlags(options: PdfSearchOptions): Int {
        var flags = 0
        if (options.matchCase) flags = flags or SEARCH_MATCH_CASE
        if (options.matchWholeWord) flags = flags or SEARCH_MATCH_WHOLE_WORD
        if (options.consecutive) flags = flags or SEARCH_CONSECUTIVE
        return flags
    }

    private fun jvmLinkBounds(link: Pointer): List<PdfQuad> {
        val pdfium = requireLibrary()
        val quadCount = pdfium.FPDFLink_CountQuadPoints(link)
        if (quadCount > 0) {
            return buildList(quadCount) {
                repeat(quadCount) { index ->
                    val quad = FsQuadPointsF()
                    if (pdfium.FPDFLink_GetQuadPoints(link, index, quad) != 0) {
                        add(
                            PdfQuad(
                                PdfPoint(quad.x1.toDouble(), quad.y1.toDouble()),
                                PdfPoint(quad.x2.toDouble(), quad.y2.toDouble()),
                                PdfPoint(quad.x3.toDouble(), quad.y3.toDouble()),
                                PdfPoint(quad.x4.toDouble(), quad.y4.toDouble()),
                            ),
                        )
                    }
                }
            }
        }
        val rect = FsRectF()
        if (pdfium.FPDFLink_GetAnnotRect(link, rect) == 0) {
            return emptyList()
        }
        return listOf(
            PdfQuad(
                PdfPoint(rect.left.toDouble(), rect.top.toDouble()),
                PdfPoint(rect.right.toDouble(), rect.top.toDouble()),
                PdfPoint(rect.left.toDouble(), rect.bottom.toDouble()),
                PdfPoint(rect.right.toDouble(), rect.bottom.toDouble()),
            ),
        )
    }

    private fun jvmDestination(
        document: Pointer,
        destination: Pointer,
    ) = with(requireLibrary()) {
        val pageIndex = FPDFDest_GetDestPageIndex(document, destination)
        check(pageIndex >= 0) { "PDFium returned an invalid destination page" }
        val parameterCount = NativeLongByReference()
        val parameterMemory = Memory(4L * Float.SIZE_BYTES)
        try {
            val viewMode =
                FPDFDest_GetView(
                    destination,
                    parameterCount,
                    parameterMemory,
                ).toInt()
            val count = parameterCount.value.toInt().coerceIn(0, 4)
            val hasX = IntByReference()
            val hasY = IntByReference()
            val hasZoom = IntByReference()
            val x = FloatByReference()
            val y = FloatByReference()
            val zoom = FloatByReference()
            FPDFDest_GetLocationInPage(
                destination,
                hasX,
                hasY,
                hasZoom,
                x,
                y,
                zoom,
            )
            pdfDestination(
                pageIndex = pageIndex,
                viewMode = viewMode,
                parameters =
                    List(count) { index ->
                        parameterMemory.getFloat(
                            index.toLong() * Float.SIZE_BYTES,
                        ).toDouble()
                    },
                hasX = hasX.value != 0,
                x = x.value.toDouble(),
                hasY = hasY.value != 0,
                y = y.value.toDouble(),
                hasZoom = hasZoom.value != 0,
                zoom = zoom.value.toDouble(),
            )
        } finally {
            parameterMemory.close()
        }
    }

    private inline fun readUtf8(
        read: (Pointer?, Long) -> NativeLong,
    ): String? {
        val requiredBytes = read(null, 0).toLong()
        if (requiredBytes <= 1) return null
        require(requiredBytes <= Int.MAX_VALUE) {
            "PDFium returned an oversized UTF-8 string"
        }
        val memory = Memory(requiredBytes)
        return try {
            val written = read(memory, requiredBytes).toLong()
            check(written == requiredBytes) {
                "PDFium returned an invalid UTF-8 result length: $written"
            }
            memory
                .getByteArray(0, (requiredBytes - 1).toInt())
                .toString(Charsets.UTF_8)
        } finally {
            memory.close()
        }
    }

    private fun document(handle: NativeDocumentHandle): Pointer =
        documents[handle.value]?.pointer
            ?: throw PdfNativeException(
                nativeErrorCode = 0,
                message = "Unknown JVM PDFium document handle",
            )

    private inline fun <T> withPage(
        document: NativeDocumentHandle,
        pageIndex: Int,
        operation: (Pointer) -> T,
    ): T {
        val pdfium = requireLibrary()
        val page =
            pdfium.FPDF_LoadPage(document(document), pageIndex)
                ?: throw PdfPageException(pageIndex)
        return try {
            operation(page)
        } finally {
            pdfium.FPDF_ClosePage(page)
        }
    }

    private inline fun readUtf16(
        emptyAsNull: Boolean,
        read: (Pointer?, Long) -> NativeLong,
    ): String? {
        val requiredBytes = read(null, 0).toLong()
        if (requiredBytes == 0L) return null
        check(requiredBytes >= 2 && requiredBytes % 2 == 0L) {
            "PDFium returned an invalid UTF-16 byte count: $requiredBytes"
        }
        val memory = Memory(requiredBytes)
        return try {
            val written = read(memory, requiredBytes).toLong()
            check(written == requiredBytes) {
                "PDFium returned an invalid UTF-16 result length: $written"
            }
            val value =
                memory
                .getByteArray(0, (requiredBytes - 2).toInt())
                .toString(Charsets.UTF_16LE)
            if (emptyAsNull && value.isEmpty()) null else value
        } finally {
            memory.close()
        }
    }

    private fun throwOpenFailure(
        errorCode: Int,
        passwordWasSupplied: Boolean,
    ): Nothing =
        when (errorCode) {
            2 -> throw PdfIoException()
            3 -> throw PdfInvalidFormatException()
            4 ->
                if (passwordWasSupplied) {
                    throw PdfIncorrectPasswordException()
                } else {
                    throw PdfPasswordRequiredException()
                }
            5 -> throw PdfUnsupportedSecurityException()
            else -> throw PdfNativeException(errorCode)
        }

    private fun pdfRotation(value: Int): PdfRotation =
        when (value) {
            0 -> PdfRotation.Degrees0
            1 -> PdfRotation.Degrees90
            2 -> PdfRotation.Degrees180
            3 -> PdfRotation.Degrees270
            else ->
                throw PdfNativeException(
                    nativeErrorCode = value,
                    message = "PDFium returned an invalid page rotation: $value",
                )
        }

    private fun UInt.toPdfPermissions(): PdfPermissions {
        fun allows(pdfBitNumber: Int): Boolean =
            this and (1u shl (pdfBitNumber - 1)) != 0u
        return PdfPermissions(
            canPrint = allows(3),
            canModify = allows(4),
            canCopy = allows(5),
            canAnnotate = allows(6),
            canFillForms = allows(9),
            canExtractForAccessibility = allows(10),
            canAssemble = allows(11),
            canPrintHighQuality = allows(12),
        )
    }

    private fun requireLibrary(): JvmPdfiumLibrary =
        library ?: error("JVM PDFium is not initialized")

    private fun unsupported(feature: String): Nothing =
        throw PdfUnsupportedFeatureException(feature)

    private const val ACTION_UNSUPPORTED: Int = 0
    private const val ACTION_GOTO: Int = 1
    private const val ACTION_REMOTE_GOTO: Int = 2
    private const val ACTION_URI: Int = 3
    private const val SEARCH_MATCH_CASE: Int = 1 shl 0
    private const val SEARCH_MATCH_WHOLE_WORD: Int = 1 shl 1
    private const val SEARCH_CONSECUTIVE: Int = 1 shl 2
}
