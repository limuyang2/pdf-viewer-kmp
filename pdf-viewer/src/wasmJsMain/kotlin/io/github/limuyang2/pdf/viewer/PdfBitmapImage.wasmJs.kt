package io.github.limuyang2.pdf.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import io.github.limuyang2.pdf.core.PdfBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

internal actual fun PdfBitmap.toComposeImageBitmap(): ImageBitmap {
    val image =
        Image.makeRaster(
            ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL),
            copyPixels(),
            stride,
        )
    return try {
        image.toComposeImageBitmap()
    } finally {
        image.close()
    }
}
