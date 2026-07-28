package io.github.limuyang2.pdf.core.internal

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

internal object WebPdfiumBackend : PdfiumBackend {
    private const val RENDER_ANNOTATIONS = 0x01
    private const val RENDER_LCD_TEXT = 0x02
    private const val RENDER_GRAYSCALE = 0x08
    private const val SEARCH_MATCH_CASE = 1 shl 0
    private const val SEARCH_MATCH_WHOLE_WORD = 1 shl 1
    private const val SEARCH_CONSECUTIVE = 1 shl 2

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
        platformWebPdfiumInterop.initialize()
    }

    override fun destroy() {
        platformWebPdfiumInterop.destroy()
    }

    override fun open(
        source: PdfSource,
        password: String?,
    ): OpenedDocument {
        val bytes =
            when (source) {
                is PdfSource.Bytes -> source.data
                is PdfSource.RandomAccess ->
                    unsupported("random-access document sources in browsers")
            }
        if (bytes.isEmpty()) throw PdfInvalidFormatException()
        require(password?.contains('\u0000') != true) {
            "password must not contain a null character"
        }
        val opened = platformWebPdfiumInterop.open(bytes, password)
        if (opened.handle == 0) {
            throwOpenFailure(opened.errorCode, password != null)
        }
        if (opened.pageCount < 0) {
            platformWebPdfiumInterop.close(opened.handle)
            throw PdfNativeException(
                nativeErrorCode = 0,
                message = "PDFium returned an invalid page count",
            )
        }
        return OpenedDocument(
            NativeDocumentHandle(opened.handle.toLong()),
            opened.pageCount,
        )
    }

    override fun close(document: NativeDocumentHandle) {
        platformWebPdfiumInterop.close(document.webHandle())
    }

    override fun documentInformation(
        document: NativeDocumentHandle,
    ): PdfDocumentInfo {
        val info =
            platformWebPdfiumInterop.documentInformation(document.webHandle())
        return PdfDocumentInfo(
            version =
                if (info.hasVersion) {
                    PdfVersion(info.version / 10, info.version % 10)
                } else {
                    null
                },
            permissions = info.permissions.toPdfPermissions(),
            securityRevision = info.securityRevision.takeIf { it >= 0 },
            hasValidCrossReferenceTable =
                info.hasValidCrossReferenceTable,
            isLinearized = null,
        )
    }

    override fun metadata(document: NativeDocumentHandle): PdfMetadata {
        fun value(tag: String): String? =
            platformWebPdfiumInterop.metadata(document.webHandle(), tag)
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
        platformWebPdfiumInterop.pageLabel(document.webHandle(), pageIndex)

    override fun pageInformation(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfPageInfo {
        val info =
            platformWebPdfiumInterop.pageInformation(
                document.webHandle(),
                pageIndex,
            ) ?: throw PdfPageException(pageIndex)
        return PdfPageInfo(
            size = PdfSize(info.width, info.height),
            rotation = pdfRotation(info.rotation),
            boundingBox =
                info.boundingBox?.let {
                    PdfRect(it.left, it.bottom, it.right, it.top)
                },
        )
    }

    override fun render(
        document: NativeDocumentHandle,
        pageIndex: Int,
        request: PdfRenderRequest,
    ): PdfBitmap {
        if (request.sourceRect != null) {
            unsupported("cropped page rendering in browsers")
        }
        val width = request.outputSize.width
        val height = request.outputSize.height
        val strideLong = width.toLong() * 4
        val byteCountLong = strideLong * height
        require(strideLong <= Int.MAX_VALUE && byteCountLong <= Int.MAX_VALUE) {
            "render output is too large"
        }
        var flags = 0
        if (request.renderAnnotations) flags = flags or RENDER_ANNOTATIONS
        if (request.grayscale) flags = flags or RENDER_GRAYSCALE
        if (request.optimizeTextForLcd) flags = flags or RENDER_LCD_TEXT
        val pixels =
            platformWebPdfiumInterop.render(
                handle = document.webHandle(),
                pageIndex = pageIndex,
                width = width,
                height = height,
                rotation = request.rotation.degrees / 90,
                backgroundArgb = request.backgroundColor.argb,
                flags = flags,
            ) ?: throw PdfPageException(pageIndex)
        check(pixels.size == byteCountLong.toInt()) {
            "PDFium returned an invalid bitmap size"
        }
        return OwnedPdfBitmap(
            width = width,
            height = height,
            stride = strideLong.toInt(),
            pixels = pixels,
        )
    }

    override fun extractText(
        document: NativeDocumentHandle,
        pageIndex: Int,
        range: PdfTextRange?,
    ): String =
        platformWebPdfiumInterop.extractText(
            handle = document.webHandle(),
            pageIndex = pageIndex,
            startCharacterIndex = range?.startCharacterIndex ?: 0,
            characterCount = range?.characterCount ?: -1,
        ) ?: throw PdfPageException(pageIndex)

    override fun bookmarks(document: NativeDocumentHandle): List<PdfBookmark> =
        unsupported("bookmarks in browsers")

    override fun thumbnail(
        document: NativeDocumentHandle,
        pageIndex: Int,
        maximumSize: PdfPixelSize,
    ): PdfBitmap? = unsupported("embedded thumbnails in browsers")

    override fun textLayout(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfTextLayout = unsupported("text layout in browsers")

    override fun search(
        document: NativeDocumentHandle,
        pageIndex: Int,
        query: String,
        options: PdfSearchOptions,
    ): List<PdfSearchMatch> =
        platformWebPdfiumInterop
            .search(
                handle = document.webHandle(),
                pageIndex = pageIndex,
                query = query,
                flags = webPdfiumSearchFlags(options),
            )?.map { match ->
                PdfSearchMatch(
                    range =
                        PdfTextRange(
                            startCharacterIndex =
                                match.startCharacterIndex,
                            characterCount = match.characterCount,
                        ),
                    bounds =
                        match.bounds.map { bounds ->
                            PdfRect(
                                left = bounds.left,
                                bottom = bounds.bottom,
                                right = bounds.right,
                                top = bounds.top,
                            )
                        },
                )
            } ?: throw PdfPageException(pageIndex)

    override fun links(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): List<PdfLink> =
        platformWebPdfiumInterop
            .links(document.webHandle(), pageIndex)
            ?.map { link ->
                val destination =
                    link.destination?.let {
                        check(it.pageIndex >= 0) {
                            "PDFium returned an invalid destination page"
                        }
                        pdfDestination(
                            pageIndex = it.pageIndex,
                            viewMode = it.viewMode,
                            parameters = it.parameters,
                            hasX = it.hasX,
                            x = it.x,
                            hasY = it.hasY,
                            y = it.y,
                            hasZoom = it.hasZoom,
                            zoom = it.zoom,
                        )
                    }
                PdfLink(
                    bounds =
                        link.bounds.map {
                            PdfQuad(
                                PdfPoint(it.x1, it.y1),
                                PdfPoint(it.x2, it.y2),
                                PdfPoint(it.x3, it.y3),
                                PdfPoint(it.x4, it.y4),
                            )
                        },
                    target =
                        when (link.targetType) {
                            PDF_LINK_TARGET_INTERNAL ->
                                PdfLinkTarget.Internal(
                                    checkNotNull(destination),
                                )
                            PDF_LINK_TARGET_URI ->
                                PdfLinkTarget.Uri(
                                    checkNotNull(link.value),
                                )
                            PDF_LINK_TARGET_REMOTE_DOCUMENT ->
                                PdfLinkTarget.RemoteDocument(
                                    link.value,
                                    destination,
                                )
                            else ->
                                PdfLinkTarget.Unsupported(link.actionType)
                        },
                )
            }
            ?: throw PdfPageException(pageIndex)

    private fun NativeDocumentHandle.webHandle(): Int {
        check(value in 1..Int.MAX_VALUE.toLong()) {
            "Invalid browser PDFium document handle: $value"
        }
        return value.toInt()
    }

    private fun webPdfiumSearchFlags(options: PdfSearchOptions): Int {
        var flags = 0
        if (options.matchCase) flags = flags or SEARCH_MATCH_CASE
        if (options.matchWholeWord) flags = flags or SEARCH_MATCH_WHOLE_WORD
        if (options.consecutive) flags = flags or SEARCH_CONSECUTIVE
        return flags
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

    private fun unsupported(feature: String): Nothing =
        throw PdfUnsupportedFeatureException(feature)
}
