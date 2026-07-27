package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfBitmap
import io.github.limuyang2.pdf.core.PdfBookmark
import io.github.limuyang2.pdf.core.PdfCapabilities
import io.github.limuyang2.pdf.core.PdfDocumentInfo
import io.github.limuyang2.pdf.core.PdfInvalidFormatException
import io.github.limuyang2.pdf.core.PdfLink
import io.github.limuyang2.pdf.core.PdfLinkTarget
import io.github.limuyang2.pdf.core.PdfMetadata
import io.github.limuyang2.pdf.core.PdfNativeException
import io.github.limuyang2.pdf.core.PdfPageException
import io.github.limuyang2.pdf.core.PdfPageInfo
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
import io.github.limuyang2.pdf.core.PdfVersion

internal object AndroidPdfiumBackend : PdfiumBackend {
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
        AndroidPdfiumNative.nativeInitialize()
    }

    override fun destroy() {
        AndroidPdfiumNative.nativeDestroy()
    }

    override fun open(
        source: PdfSource,
        password: String?,
    ): OpenedDocument {
        val bytes =
            when (source) {
                is PdfSource.Bytes -> source.data
                is PdfSource.RandomAccess ->
                    unsupported("random-access document sources on Android")
            }
        if (bytes.isEmpty()) throw PdfInvalidFormatException()
        require(password?.contains('\u0000') != true) {
            "password must not contain a null character"
        }

        val result =
            AndroidPdfiumNative.nativeOpen(bytes, password)
                ?: throw PdfNativeException(
                    nativeErrorCode = 0,
                    message = "The Android PDFium bridge could not open the document",
                )
        check(result.size == 4) {
            "The Android PDFium bridge returned an invalid open result"
        }
        val nativeHandle = result[0]
        if (nativeHandle == 0L) {
            if (result[2] == 0L) {
                throw PdfNativeException(
                    nativeErrorCode = result[3].toInt(),
                    message = "The shared PDFium bridge could not open the document",
                )
            }
            throw androidPdfiumOpenFailure(
                errorCode = result[2],
                passwordWasSupplied = password != null,
            )
        }
        val pageCount =
            result[1].also {
                if (it !in 0..Int.MAX_VALUE.toLong()) {
                    AndroidPdfiumNative.nativeClose(nativeHandle)
                    throw PdfNativeException(
                        nativeErrorCode = 0,
                        message = "PDFium returned an invalid page count: $it",
                    )
                }
            }
        return OpenedDocument(
            handle = NativeDocumentHandle(nativeHandle),
            pageCount = pageCount.toInt(),
        )
    }

    override fun close(document: NativeDocumentHandle) {
        AndroidPdfiumNative.nativeClose(document.value)
    }

    override fun pageInformation(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfPageInfo {
        val values =
            AndroidPdfiumNative.nativePageInformation(document.value, pageIndex)
                ?: throw PdfPageException(pageIndex)
        if (values.size != 8) throw PdfPageException(pageIndex)
        return PdfPageInfo(
            size = PdfSize(values[0], values[1]),
            rotation = pdfRotation(values[2].toInt()),
            boundingBox =
                if (values[7] == 0.0) {
                    null
                } else {
                    PdfRect(
                        left = values[3],
                        bottom = values[4],
                        right = values[5],
                        top = values[6],
                    )
                },
        )
    }

    override fun render(
        document: NativeDocumentHandle,
        pageIndex: Int,
        request: PdfRenderRequest,
    ): PdfBitmap {
        if (request.sourceRect != null) {
            unsupported("cropped page rendering on Android")
        }
        val width = request.outputSize.width
        val height = request.outputSize.height
        val strideLong = width.toLong() * 4L
        val byteCountLong = strideLong * height
        require(strideLong <= Int.MAX_VALUE && byteCountLong <= Int.MAX_VALUE) {
            "render output is too large"
        }
        val pixels =
            AndroidPdfiumNative.nativeRender(
                handle = document.value,
                pageIndex = pageIndex,
                width = width,
                height = height,
                rotation = request.rotation.degrees / 90,
                backgroundColor = request.backgroundColor.argb.toLong(),
                renderAnnotations = request.renderAnnotations,
                grayscale = request.grayscale,
                lcdText = request.optimizeTextForLcd,
            ) ?: throw PdfPageException(pageIndex)
        check(pixels.size == byteCountLong.toInt()) {
            "The Android PDFium bridge returned an invalid bitmap size"
        }
        return OwnedPdfBitmap(
            width = width,
            height = height,
            stride = strideLong.toInt(),
            pixels = pixels,
        )
    }

    override fun documentInformation(
        document: NativeDocumentHandle,
    ): PdfDocumentInfo {
        val values =
            AndroidPdfiumNative.nativeDocumentInformation(document.value)
                ?: throw PdfNativeException(
                    nativeErrorCode = 0,
                    message = "The shared PDFium bridge could not read document information",
                )
        check(values.size == 7) {
            "The Android PDFium bridge returned invalid document information"
        }
        if (values[0] != 0L) {
            throw PdfNativeException(values[0].toInt())
        }
        val encodedVersion = values[2].toInt()
        return PdfDocumentInfo(
            version =
                if (values[1] == 0L) {
                    null
                } else {
                    PdfVersion(
                        major = encodedVersion / 10,
                        minor = encodedVersion % 10,
                    )
                },
            permissions = values[3].toULong().toPdfPermissions(),
            securityRevision = values[4].toInt().takeIf { it >= 0 },
            hasValidCrossReferenceTable = values[5] != 0L,
            isLinearized =
                when (values[6]) {
                    0L -> false
                    1L -> true
                    else -> null
                },
        )
    }

    override fun metadata(
        document: NativeDocumentHandle,
    ): PdfMetadata {
        fun value(tag: String): String? =
            AndroidPdfiumNative.nativeMetadata(document.value, tag)
                ?.takeIf(String::isNotEmpty)
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

    override fun bookmarks(
        document: NativeDocumentHandle,
    ): List<PdfBookmark> = unsupported("bookmarks on Android")

    override fun pageLabel(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): String? =
        AndroidPdfiumNative.nativePageLabel(document.value, pageIndex)

    override fun thumbnail(
        document: NativeDocumentHandle,
        pageIndex: Int,
        maximumSize: PdfPixelSize,
    ): PdfBitmap? = unsupported("embedded thumbnails on Android")

    override fun extractText(
        document: NativeDocumentHandle,
        pageIndex: Int,
        range: PdfTextRange?,
    ): String =
        AndroidPdfiumNative.nativeExtractText(
            handle = document.value,
            pageIndex = pageIndex,
            startCharacterIndex = range?.startCharacterIndex ?: 0,
            characterCount = range?.characterCount ?: -1,
        ) ?: throw PdfPageException(pageIndex)

    override fun textLayout(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfTextLayout = unsupported("text layout on Android")

    override fun search(
        document: NativeDocumentHandle,
        pageIndex: Int,
        query: String,
        options: PdfSearchOptions,
    ): List<PdfSearchMatch> =
        AndroidPdfiumNative.nativeSearch(
            handle = document.value,
            pageIndex = pageIndex,
            query = query,
            flags = androidPdfiumSearchFlags(options),
        )?.map(::androidPdfSearchMatch)
            ?: throw PdfPageException(pageIndex)

    override fun links(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): List<PdfLink> =
        AndroidPdfiumNative.nativeLinks(document.value, pageIndex)
            ?.map(::androidPdfLink)
            ?: throw PdfPageException(pageIndex)

    private fun androidPdfLink(link: AndroidNativePdfLink): PdfLink {
        require(link.bounds.size % LINK_QUAD_VALUE_COUNT == 0) {
            "The Android PDFium bridge returned invalid link bounds"
        }
        require(link.destination.size == DESTINATION_VALUE_COUNT) {
            "The Android PDFium bridge returned an invalid destination"
        }
        val destinationValues = link.destination
        val parameterCount =
            destinationValues[2].toInt().coerceIn(0, 4)
        val destination =
            if (destinationValues[0] >= 0.0) {
                pdfDestination(
                    pageIndex = destinationValues[0].toInt(),
                    viewMode = destinationValues[1].toInt(),
                    parameters =
                        List(parameterCount) { destinationValues[it + 3] },
                    hasX = destinationValues[7] != 0.0,
                    x = destinationValues[8],
                    hasY = destinationValues[9] != 0.0,
                    y = destinationValues[10],
                    hasZoom = destinationValues[11] != 0.0,
                    zoom = destinationValues[12],
                )
            } else {
                null
            }
        val value =
            link.valueUtf8?.decodeToString(throwOnInvalidSequence = false)
        val target =
            when (link.targetType) {
                PDF_LINK_TARGET_INTERNAL ->
                    PdfLinkTarget.Internal(
                        checkNotNull(destination) {
                            "An internal PDF link has no destination"
                        },
                    )
                PDF_LINK_TARGET_URI ->
                    PdfLinkTarget.Uri(
                        checkNotNull(value) {
                            "A PDF URI link has no URI"
                        },
                    )
                PDF_LINK_TARGET_REMOTE_DOCUMENT ->
                    PdfLinkTarget.RemoteDocument(value, destination)
                else ->
                    PdfLinkTarget.Unsupported(link.nativeActionType)
            }
        return PdfLink(
            bounds =
                link.bounds
                    .asList()
                    .chunked(LINK_QUAD_VALUE_COUNT)
                    .map { values ->
                        PdfQuad(
                            PdfPoint(values[0], values[1]),
                            PdfPoint(values[2], values[3]),
                            PdfPoint(values[4], values[5]),
                            PdfPoint(values[6], values[7]),
                        )
                    },
            target = target,
        )
    }

    private fun androidPdfSearchMatch(
        match: AndroidNativePdfSearchMatch,
    ): PdfSearchMatch {
        require(match.bounds.size % SEARCH_RECT_VALUE_COUNT == 0) {
            "The Android PDFium bridge returned invalid search bounds"
        }
        val bounds =
            buildList(match.bounds.size / SEARCH_RECT_VALUE_COUNT) {
                var offset = 0
                while (offset < match.bounds.size) {
                    add(
                        PdfRect(
                            left = match.bounds[offset],
                            bottom = match.bounds[offset + 1],
                            right = match.bounds[offset + 2],
                            top = match.bounds[offset + 3],
                        ),
                    )
                    offset += SEARCH_RECT_VALUE_COUNT
                }
            }
        return PdfSearchMatch(
            range =
                PdfTextRange(
                    startCharacterIndex = match.startCharacterIndex,
                    characterCount = match.characterCount,
                ),
            bounds = bounds,
        )
    }

    private fun androidPdfiumSearchFlags(options: PdfSearchOptions): Int {
        var flags = 0
        if (options.matchCase) flags = flags or SEARCH_MATCH_CASE
        if (options.matchWholeWord) flags = flags or SEARCH_MATCH_WHOLE_WORD
        if (options.consecutive) flags = flags or SEARCH_CONSECUTIVE
        return flags
    }

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

    private fun unsupported(feature: String): Nothing =
        throw PdfUnsupportedFeatureException(feature)

    private const val LINK_QUAD_VALUE_COUNT: Int = 8
    private const val DESTINATION_VALUE_COUNT: Int = 13
    private const val SEARCH_RECT_VALUE_COUNT: Int = 4
    private const val SEARCH_MATCH_CASE: Int = 1 shl 0
    private const val SEARCH_MATCH_WHOLE_WORD: Int = 1 shl 1
    private const val SEARCH_CONSECUTIVE: Int = 1 shl 2
}
