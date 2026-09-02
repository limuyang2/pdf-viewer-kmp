package io.github.limuyang2.pdf.core

/**
 * Describes how a page is rendered into a bitmap.
 */
data class PdfRenderRequest(
    /** Destination bitmap size in pixels. */
    val outputSize: PdfPixelSize,
    /**
     * Reserved for cropped rendering. Not implemented by the current
     * backends; must be `null`.
     */
    val sourceRect: PdfRect? = null,
    /** Extra rotation applied on top of the page's intrinsic rotation. */
    val rotation: PdfRotation = PdfRotation.Degrees0,
    /** Fill color painted behind the page content. */
    val backgroundColor: PdfColor = PdfColor.White,
    /** Whether annotation appearances are painted on top of page content. */
    val renderAnnotations: Boolean = true,
    /** Renders the page without color information. */
    val grayscale: Boolean = false,
    /** Optimizes text rendering for LCD subpixel display. */
    val optimizeTextForLcd: Boolean = false,
)

/**
 * A rendered page image owned by the caller.
 *
 * The buffer uses [PdfPixelFormat.Bgra8888] on the current backends. Closing
 * releases the pixel buffer; all other properties keep working.
 */
interface PdfBitmap : AutoCloseable {
    /** Bitmap width in pixels. */
    val width: Int
    /** Bitmap height in pixels. */
    val height: Int
    /**
     * Byte distance between the start of two adjacent rows. Rows are not
     * guaranteed to be tightly packed, so always use [stride] when indexing
     * the buffer.
     */
    val stride: Int
    /** Pixel layout of the buffer. */
    val format: PdfPixelFormat
    /** Whether [close] has been called. */
    val isClosed: Boolean

    /**
     * Returns a copy of the full pixel buffer, including per-row padding.
     */
    fun copyPixels(): ByteArray

    /**
     * Copies the full pixel buffer into [destination] starting at
     * [destinationOffset]. The destination must have enough remaining
     * capacity for the whole buffer.
     */
    fun copyPixels(
        destination: ByteArray,
        destinationOffset: Int = 0,
    )

    override fun close()
}

/**
 * Pixel layouts a [PdfBitmap] can use.
 */
enum class PdfPixelFormat(
    /** Bytes occupied by one pixel. */
    val bytesPerPixel: Int,
) {
    /** 8-bit blue, green, red, alpha channels in that byte order. */
    Bgra8888(4),

    /** 8-bit blue, green, red channels plus an unused byte. */
    Bgrx8888(4),

    /** One 8-bit luminance channel. */
    Gray8(1),
}
