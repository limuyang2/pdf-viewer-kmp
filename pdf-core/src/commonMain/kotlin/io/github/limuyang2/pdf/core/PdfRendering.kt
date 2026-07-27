package io.github.limuyang2.pdf.core

data class PdfRenderRequest(
    val outputSize: PdfPixelSize,
    val sourceRect: PdfRect? = null,
    val rotation: PdfRotation = PdfRotation.Degrees0,
    val backgroundColor: PdfColor = PdfColor.White,
    val renderAnnotations: Boolean = true,
    val grayscale: Boolean = false,
    val optimizeTextForLcd: Boolean = false,
)

interface PdfBitmap : AutoCloseable {
    val width: Int
    val height: Int
    val stride: Int
    val format: PdfPixelFormat
    val isClosed: Boolean

    fun copyPixels(): ByteArray

    fun copyPixels(
        destination: ByteArray,
        destinationOffset: Int = 0,
    )

    override fun close()
}

enum class PdfPixelFormat(
    val bytesPerPixel: Int,
) {
    Bgra8888(4),
    Bgrx8888(4),
    Gray8(1),
}
