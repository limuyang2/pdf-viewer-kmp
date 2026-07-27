package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfBitmap
import io.github.limuyang2.pdf.core.PdfBookmark
import io.github.limuyang2.pdf.core.PdfCapabilities
import io.github.limuyang2.pdf.core.PdfDocumentInfo
import io.github.limuyang2.pdf.core.PdfInvalidFormatException
import io.github.limuyang2.pdf.core.PdfLink
import io.github.limuyang2.pdf.core.PdfMetadata
import io.github.limuyang2.pdf.core.PdfNativeException
import io.github.limuyang2.pdf.core.PdfPageException
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPermissions
import io.github.limuyang2.pdf.core.PdfPixelSize
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
    ): List<PdfSearchMatch> = unsupported("text search on Android")

    override fun links(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): List<PdfLink> = unsupported("links on Android")

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
}
