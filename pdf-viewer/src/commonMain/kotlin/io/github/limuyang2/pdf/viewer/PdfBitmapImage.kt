package io.github.limuyang2.pdf.viewer

import androidx.compose.ui.graphics.ImageBitmap
import io.github.limuyang2.pdf.core.PdfBitmap

internal expect fun PdfBitmap.toComposeImageBitmap(): ImageBitmap
