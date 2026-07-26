@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfBitmap
import io.github.limuyang2.pdf.viewer.PdfBookmark
import io.github.limuyang2.pdf.viewer.PdfCapabilities
import io.github.limuyang2.pdf.viewer.PdfClosedException
import io.github.limuyang2.pdf.viewer.PdfDocumentInfo
import io.github.limuyang2.pdf.viewer.PdfInvalidFormatException
import io.github.limuyang2.pdf.viewer.PdfLink
import io.github.limuyang2.pdf.viewer.PdfMetadata
import io.github.limuyang2.pdf.viewer.PdfNativeException
import io.github.limuyang2.pdf.viewer.PdfPageException
import io.github.limuyang2.pdf.viewer.PdfPageInfo
import io.github.limuyang2.pdf.viewer.PdfPixelSize
import io.github.limuyang2.pdf.viewer.PdfPermissions
import io.github.limuyang2.pdf.viewer.PdfRect
import io.github.limuyang2.pdf.viewer.PdfRenderRequest
import io.github.limuyang2.pdf.viewer.PdfRotation
import io.github.limuyang2.pdf.viewer.PdfSearchMatch
import io.github.limuyang2.pdf.viewer.PdfSearchOptions
import io.github.limuyang2.pdf.viewer.PdfSize
import io.github.limuyang2.pdf.viewer.PdfSource
import io.github.limuyang2.pdf.viewer.PdfTextLayout
import io.github.limuyang2.pdf.viewer.PdfTextRange
import io.github.limuyang2.pdf.viewer.PdfUnsupportedFeatureException
import io.github.limuyang2.pdf.viewer.PdfVersion
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDFBitmap_BGRA
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDFBitmap_CreateEx
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDFBitmap_Destroy
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDFBitmap_FillRect
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_ANNOT
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_CloseDocument
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_ClosePage
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_DocumentHasValidCrossReferenceTable
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_DestroyLibrary
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_GetDocPermissions
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_GetFileVersion
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_GetLastError
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_GetMetaText
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_GetPageBoundingBox
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_GetPageCount
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_GetPageHeightF
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_GetPageLabel
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_GetPageWidthF
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_GetSecurityHandlerRevision
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_GRAYSCALE
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_InitLibraryWithConfig
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_LCD_TEXT
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_LIBRARY_CONFIG
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_LoadMemDocument64
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_LoadPage
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_RenderPageBitmap
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDFPage_GetRotation
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDFText_ClosePage
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDFText_CountChars
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDFText_GetText
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDFText_LoadPage
import io.github.limuyang2.pdf.viewer.internal.pdfium.FS_RECTF
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pin
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

internal object IosPdfiumBackend : PdfiumBackend {
    private val documents = mutableMapOf<Long, IosPdfDocument>()
    private var nextHandle = 1L

    override val capabilities: PdfCapabilities =
        PdfCapabilities(
            text = true,
            search = false,
            bookmarks = false,
            links = false,
            thumbnails = false,
            progressiveLoading = false,
            progressiveRendering = false,
            forms = false,
            editing = false,
            javascriptExecution = false,
            xfa = false,
        )

    override fun initialize() {
        memScoped {
            val config = alloc<FPDF_LIBRARY_CONFIG>()
            config.version = 2
            config.m_pUserFontPaths = null
            config.m_pIsolate = null
            config.m_v8EmbedderSlot = 0u
            FPDF_InitLibraryWithConfig(config.ptr)
        }
    }

    override fun destroy() {
        check(documents.isEmpty()) {
            "Cannot destroy PDFium while iOS documents are still open"
        }
        FPDF_DestroyLibrary()
    }

    override suspend fun open(
        source: PdfSource,
        password: String?,
    ): OpenedDocument {
        val bytes =
            when (source) {
                is PdfSource.Bytes -> source.data
                is PdfSource.RandomAccess ->
                    unsupported("random-access document sources on iOS")
            }
        if (bytes.isEmpty()) {
            throw PdfInvalidFormatException()
        }

        val pinnedSource = bytes.pin()
        val document =
            FPDF_LoadMemDocument64(
                data_buf = pinnedSource.addressOf(0),
                size = bytes.size.toULong(),
                password = password,
            )
        if (document == null) {
            val errorCode = FPDF_GetLastError()
            pinnedSource.unpin()
            throw pdfiumOpenFailure(
                errorCode = errorCode,
                passwordWasSupplied = password != null,
            )
        }

        try {
            val pageCount = FPDF_GetPageCount(document)
            if (pageCount < 0) {
                throw PdfNativeException(
                    nativeErrorCode = 0,
                    message = "PDFium returned an invalid page count: $pageCount",
                )
            }
            check(nextHandle < Long.MAX_VALUE) {
                "The iOS PDF document handle space is exhausted"
            }
            val handleValue = nextHandle++
            documents[handleValue] =
                IosPdfDocument(
                    document = document,
                    source = bytes,
                    pinnedSource = pinnedSource,
                )
            return OpenedDocument(
                handle = NativeDocumentHandle(handleValue),
                pageCount = pageCount,
            )
        } catch (failure: Throwable) {
            FPDF_CloseDocument(document)
            pinnedSource.unpin()
            throw failure
        }
    }

    override fun close(document: NativeDocumentHandle) {
        val iosDocument =
            documents.remove(document.value)
                ?: throw PdfClosedException("PDF document")
        try {
            FPDF_CloseDocument(iosDocument.document)
        } finally {
            iosDocument.pinnedSource.unpin()
        }
    }

    override suspend fun pageInformation(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfPageInfo =
        withPage(document, pageIndex) { page ->
            val boundingBox =
                memScoped {
                    val rect = alloc<FS_RECTF>()
                    if (FPDF_GetPageBoundingBox(page, rect.ptr) == 0) {
                        null
                    } else {
                        PdfRect(
                            left = rect.left.toDouble(),
                            bottom = rect.bottom.toDouble(),
                            right = rect.right.toDouble(),
                            top = rect.top.toDouble(),
                        )
                    }
                }
            PdfPageInfo(
                size =
                    PdfSize(
                        width = FPDF_GetPageWidthF(page).toDouble(),
                        height = FPDF_GetPageHeightF(page).toDouble(),
                    ),
                rotation = pdfRotation(FPDFPage_GetRotation(page)),
                boundingBox = boundingBox,
            )
        }

    override suspend fun render(
        document: NativeDocumentHandle,
        pageIndex: Int,
        request: PdfRenderRequest,
    ): PdfBitmap {
        if (request.sourceRect != null) {
            unsupported("cropped page rendering on iOS")
        }
        val width = request.outputSize.width
        val height = request.outputSize.height
        val strideLong = width.toLong() * 4L
        val byteCountLong = strideLong * height.toLong()
        require(strideLong <= Int.MAX_VALUE && byteCountLong <= Int.MAX_VALUE) {
            "render output is too large"
        }
        val stride = strideLong.toInt()
        val pixels = ByteArray(byteCountLong.toInt())

        withPage(document, pageIndex) { page ->
            pixels.usePinned { pinnedPixels ->
                val bitmap =
                    FPDFBitmap_CreateEx(
                        width = width,
                        height = height,
                        format = FPDFBitmap_BGRA,
                        first_scan = pinnedPixels.addressOf(0),
                        stride = stride,
                    ) ?: throw PdfNativeException(
                        nativeErrorCode = 0,
                        message = "PDFium could not create a render bitmap",
                    )
                try {
                    if (
                        FPDFBitmap_FillRect(
                            bitmap = bitmap,
                            left = 0,
                            top = 0,
                            width = width,
                            height = height,
                            color = request.backgroundColor.argb.toULong(),
                        ) == 0
                    ) {
                        throw PdfNativeException(
                            nativeErrorCode = 0,
                            message = "PDFium could not fill the render bitmap",
                        )
                    }
                    var flags = 0
                    if (request.renderAnnotations) flags = flags or FPDF_ANNOT
                    if (request.grayscale) flags = flags or FPDF_GRAYSCALE
                    if (request.optimizeTextForLcd) flags = flags or FPDF_LCD_TEXT
                    FPDF_RenderPageBitmap(
                        bitmap = bitmap,
                        page = page,
                        start_x = 0,
                        start_y = 0,
                        size_x = width,
                        size_y = height,
                        rotate = request.rotation.degrees / 90,
                        flags = flags,
                    )
                } finally {
                    FPDFBitmap_Destroy(bitmap)
                }
            }
        }
        return IosPdfBitmap(width, height, stride, pixels)
    }

    override suspend fun extractText(
        document: NativeDocumentHandle,
        pageIndex: Int,
        range: PdfTextRange?,
    ): String =
        withPage(document, pageIndex) { page ->
            val textPage =
                FPDFText_LoadPage(page)
                    ?: throw PdfPageException(pageIndex)
            try {
                val totalCharacterCount = FPDFText_CountChars(textPage)
                if (totalCharacterCount < 0) {
                    throw PdfPageException(pageIndex)
                }
                val start = range?.startCharacterIndex ?: 0
                val count = range?.characterCount ?: totalCharacterCount
                require(start.toLong() + count <= totalCharacterCount) {
                    "text range exceeds the page character count"
                }
                if (count == 0) return@withPage ""

                val utf16 = UShortArray(count + 1)
                val written =
                    utf16.usePinned { pinnedText ->
                        FPDFText_GetText(
                            text_page = textPage,
                            start_index = start,
                            count = count,
                            result = pinnedText.addressOf(0),
                        )
                    }
                if (written <= 0) {
                    throw PdfPageException(pageIndex)
                }
                buildString(written - 1) {
                    repeat(written - 1) { index ->
                        append(utf16[index].toInt().toChar())
                    }
                }
            } finally {
                FPDFText_ClosePage(textPage)
            }
        }

    override suspend fun documentInformation(
        document: NativeDocumentHandle,
    ): PdfDocumentInfo {
        val nativeDocument = requireDocument(document).document
        val version =
            memScoped {
                val encodedVersion = alloc<IntVar>()
                if (FPDF_GetFileVersion(nativeDocument, encodedVersion.ptr) == 0) {
                    null
                } else {
                    PdfVersion(
                        major = encodedVersion.value / 10,
                        minor = encodedVersion.value % 10,
                    )
                }
            }
        val permissionFlags = FPDF_GetDocPermissions(nativeDocument)
        val securityRevision =
            FPDF_GetSecurityHandlerRevision(nativeDocument).takeIf { it >= 0 }
        return PdfDocumentInfo(
            version = version,
            permissions = permissionFlags.toPdfPermissions(),
            securityRevision = securityRevision,
            hasValidCrossReferenceTable =
                FPDF_DocumentHasValidCrossReferenceTable(nativeDocument) != 0,
            isLinearized = null,
        )
    }

    override suspend fun metadata(
        document: NativeDocumentHandle,
    ): PdfMetadata {
        val nativeDocument = requireDocument(document).document
        fun value(tag: String): String? =
            readPdfiumUtf16(emptyIsNull = true) { buffer, byteCount ->
                FPDF_GetMetaText(nativeDocument, tag, buffer, byteCount)
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

    override suspend fun bookmarks(
        document: NativeDocumentHandle,
    ): List<PdfBookmark> = unsupported("bookmarks on iOS")

    override suspend fun pageLabel(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): String? {
        val nativeDocument = requireDocument(document).document
        return readPdfiumUtf16(emptyIsNull = false) { buffer, byteCount ->
            FPDF_GetPageLabel(
                document = nativeDocument,
                page_index = pageIndex,
                buffer = buffer,
                buflen = byteCount,
            )
        }
    }

    override suspend fun thumbnail(
        document: NativeDocumentHandle,
        pageIndex: Int,
        maximumSize: PdfPixelSize,
    ): PdfBitmap? = unsupported("embedded thumbnails on iOS")

    override suspend fun textLayout(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfTextLayout = unsupported("text layout on iOS")

    override suspend fun search(
        document: NativeDocumentHandle,
        pageIndex: Int,
        query: String,
        options: PdfSearchOptions,
    ): List<PdfSearchMatch> = unsupported("text search on iOS")

    override suspend fun links(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): List<PdfLink> = unsupported("links on iOS")

    private inline fun <T> withPage(
        document: NativeDocumentHandle,
        pageIndex: Int,
        operation: (io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_PAGE) -> T,
    ): T {
        val nativeDocument = requireDocument(document)
        val page =
            FPDF_LoadPage(nativeDocument.document, pageIndex)
                ?: throw PdfPageException(pageIndex)
        return try {
            operation(page)
        } finally {
            FPDF_ClosePage(page)
        }
    }

    private fun requireDocument(handle: NativeDocumentHandle): IosPdfDocument =
        documents[handle.value] ?: throw PdfClosedException("PDF document")

    private fun pdfRotation(value: Int): PdfRotation =
        when (value) {
            0 -> PdfRotation.Degrees0
            1 -> PdfRotation.Degrees90
            2 -> PdfRotation.Degrees180
            3 -> PdfRotation.Degrees270
            else -> throw PdfNativeException(
                nativeErrorCode = value,
                message = "PDFium returned an invalid page rotation: $value",
            )
        }

    private fun ULong.toPdfPermissions(): PdfPermissions {
        fun allows(pdfBitNumber: Int): Boolean =
            this and (1uL shl (pdfBitNumber - 1)) != 0uL
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

    private fun readPdfiumUtf16(
        emptyIsNull: Boolean,
        load: (buffer: CValuesRef<*>?, byteCount: ULong) -> ULong,
    ): String? {
        val requiredBytes = load(null, 0u)
        if (requiredBytes == 0uL || requiredBytes % 2uL != 0uL) {
            return null
        }
        if (requiredBytes == 2uL && emptyIsNull) return null
        val unitCount = (requiredBytes / 2uL).toLong()
        if (unitCount > Int.MAX_VALUE) {
            throw PdfNativeException(
                nativeErrorCode = 0,
                message = "PDFium returned an oversized UTF-16 value",
            )
        }
        val utf16 = UShortArray(unitCount.toInt())
        val writtenBytes =
            utf16.usePinned { pinned ->
                load(pinned.addressOf(0), requiredBytes)
            }
        if (writtenBytes != requiredBytes) {
            throw PdfNativeException(
                nativeErrorCode = 0,
                message = "PDFium could not read a UTF-16 value",
            )
        }
        return buildString(utf16.size - 1) {
            repeat(utf16.size - 1) { index ->
                append(utf16[index].toInt().toChar())
            }
        }
    }

    private fun unsupported(feature: String): Nothing =
        throw PdfUnsupportedFeatureException(feature)
}
