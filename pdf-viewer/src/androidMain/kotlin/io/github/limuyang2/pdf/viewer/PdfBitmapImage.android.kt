package io.github.limuyang2.pdf.viewer

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import io.github.limuyang2.pdf.core.PdfBitmap

internal actual fun PdfBitmap.toComposeImageBitmap(): ImageBitmap {
    val source = copyPixels()
    val colors = IntArray(width * height)
    for (y in 0 until height) {
        val sourceRow = y * stride
        val destinationRow = y * width
        for (x in 0 until width) {
            val offset = sourceRow + x * 4
            val blue = source[offset].toInt() and 0xff
            val green = source[offset + 1].toInt() and 0xff
            val red = source[offset + 2].toInt() and 0xff
            val alpha = source[offset + 3].toInt() and 0xff
            colors[destinationRow + x] =
                (alpha shl 24) or
                    (red shl 16) or
                    (green shl 8) or
                    blue
        }
    }
    return Bitmap
        .createBitmap(colors, width, height, Bitmap.Config.ARGB_8888)
        .asImageBitmap()
}
