package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfBitmap
import io.github.limuyang2.pdf.core.PdfClosedException
import io.github.limuyang2.pdf.core.PdfPixelFormat

internal class OwnedPdfBitmap(
    override val width: Int,
    override val height: Int,
    override val stride: Int,
    pixels: ByteArray,
) : PdfBitmap {
    private var pixels: ByteArray? = pixels

    override val format: PdfPixelFormat = PdfPixelFormat.Bgra8888

    override val isClosed: Boolean
        get() = pixels == null

    override fun copyPixels(): ByteArray = requirePixels().copyOf()

    override fun copyPixels(
        destination: ByteArray,
        destinationOffset: Int,
    ) {
        val source = requirePixels()
        require(destinationOffset >= 0) {
            "destinationOffset must be non-negative"
        }
        require(destinationOffset.toLong() + source.size <= destination.size) {
            "destination does not have enough remaining capacity"
        }
        source.copyInto(destination, destinationOffset)
    }

    override fun close() {
        pixels = null
    }

    private fun requirePixels(): ByteArray =
        pixels ?: throw PdfClosedException("PDF bitmap")
}
