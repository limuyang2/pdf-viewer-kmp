package io.github.limuyang2.pdf.viewer

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.limuyang2.pdf.core.PdfBitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal actual fun PdfBitmap.toComposeImageBitmap(): ImageBitmap {
    val colors = bgraPixelsToArgbColors(copyPixels(), width, height, stride)
    return Bitmap
        .createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}

internal fun bgraPixelsToArgbColors(
    source: ByteArray,
    width: Int,
    height: Int,
    stride: Int,
): IntArray {
    require(width > 0) { "width must be positive" }
    require(height > 0) { "height must be positive" }
    val packedRowBytes = width.toLong() * BYTES_PER_PIXEL
    require(packedRowBytes <= Int.MAX_VALUE && stride >= packedRowBytes.toInt()) {
        "stride must contain at least width pixels"
    }
    require(stride % BYTES_PER_PIXEL == 0) {
        "stride must be a multiple of $BYTES_PER_PIXEL"
    }
    val requiredBytes = stride.toLong() * height
    require(requiredBytes <= source.size) {
        "source does not contain every pixel row"
    }

    val pixelCount = width.toLong() * height
    require(pixelCount <= Int.MAX_VALUE) { "bitmap contains too many pixels" }
    val colors = IntArray(pixelCount.toInt())
    val pixels =
        ByteBuffer
            .wrap(source)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asIntBuffer()
    if (stride == packedRowBytes.toInt()) {
        pixels.get(colors)
        return colors
    }

    val strideInts = stride / BYTES_PER_PIXEL
    repeat(height) { row ->
        pixels.position(row * strideInts)
        pixels.get(colors, row * width, width)
    }
    return colors
}

private const val BYTES_PER_PIXEL = 4
