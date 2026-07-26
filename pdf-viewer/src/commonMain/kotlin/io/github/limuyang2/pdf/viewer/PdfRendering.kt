package io.github.limuyang2.pdf.viewer

public data class PdfRenderRequest(
    val outputSize: PdfPixelSize,
    val sourceRect: PdfRect? = null,
    val rotation: PdfRotation = PdfRotation.Degrees0,
    val backgroundColor: PdfColor = PdfColor.White,
    val renderAnnotations: Boolean = true,
    val grayscale: Boolean = false,
    val optimizeTextForLcd: Boolean = false,
)

public interface PdfBitmap : AutoCloseable {
    public val width: Int
    public val height: Int
    public val stride: Int
    public val format: PdfPixelFormat
    public val isClosed: Boolean

    public fun copyPixels(): ByteArray

    public fun copyPixels(
        destination: ByteArray,
        destinationOffset: Int = 0,
    )

    override fun close()
}

public enum class PdfPixelFormat(
    public val bytesPerPixel: Int,
) {
    Bgra8888(4),
    Bgrx8888(4),
    Gray8(1),
}
