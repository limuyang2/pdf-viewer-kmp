package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfBitmap
import io.github.limuyang2.pdf.viewer.PdfBookmark
import io.github.limuyang2.pdf.viewer.PdfCapabilities
import io.github.limuyang2.pdf.viewer.PdfDocumentInfo
import io.github.limuyang2.pdf.viewer.PdfInvalidFormatException
import io.github.limuyang2.pdf.viewer.PdfLink
import io.github.limuyang2.pdf.viewer.PdfMetadata
import io.github.limuyang2.pdf.viewer.PdfNativeException
import io.github.limuyang2.pdf.viewer.PdfPageException
import io.github.limuyang2.pdf.viewer.PdfPageInfo
import io.github.limuyang2.pdf.viewer.PdfPixelSize
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

internal object AndroidPdfiumBackend : PdfiumBackend {
    override val capabilities: PdfCapabilities =
        PdfCapabilities(
            text = false,
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
        AndroidPdfiumNative.nativeInitialize()
    }

    override fun destroy() {
        AndroidPdfiumNative.nativeDestroy()
    }

    override suspend fun open(
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
        check(result.size == 3) {
            "The Android PDFium bridge returned an invalid open result"
        }
        val nativeHandle = result[0]
        if (nativeHandle == 0L) {
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

    override suspend fun pageInformation(
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

    override suspend fun render(
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
        return AndroidPdfBitmap(
            width = width,
            height = height,
            stride = strideLong.toInt(),
            pixels = pixels,
        )
    }

    override suspend fun documentInformation(
        document: NativeDocumentHandle,
    ): PdfDocumentInfo = unsupported("document information on Android")

    override suspend fun metadata(
        document: NativeDocumentHandle,
    ): PdfMetadata = unsupported("document metadata on Android")

    override suspend fun bookmarks(
        document: NativeDocumentHandle,
    ): List<PdfBookmark> = unsupported("bookmarks on Android")

    override suspend fun pageLabel(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): String? = unsupported("page labels on Android")

    override suspend fun thumbnail(
        document: NativeDocumentHandle,
        pageIndex: Int,
        maximumSize: PdfPixelSize,
    ): PdfBitmap? = unsupported("embedded thumbnails on Android")

    override suspend fun extractText(
        document: NativeDocumentHandle,
        pageIndex: Int,
        range: PdfTextRange?,
    ): String = unsupported("text extraction on Android")

    override suspend fun textLayout(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfTextLayout = unsupported("text layout on Android")

    override suspend fun search(
        document: NativeDocumentHandle,
        pageIndex: Int,
        query: String,
        options: PdfSearchOptions,
    ): List<PdfSearchMatch> = unsupported("text search on Android")

    override suspend fun links(
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

    private fun unsupported(feature: String): Nothing =
        throw PdfUnsupportedFeatureException(feature)
}
