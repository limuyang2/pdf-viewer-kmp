package io.github.limuyang2.pdf.viewer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Visual treatment for all matches and the currently selected match.
 */
@Immutable
data class PdfSearchHighlightStyle(
    val match: PdfSearchHighlightDecoration =
        PdfSearchHighlightDecoration(
            fillColor = Color(0x66FFEB3B),
        ),
    val selectedMatch: PdfSearchHighlightDecoration =
        PdfSearchHighlightDecoration(
            fillColor = Color(0x80FF9800),
            strokeColor = Color(0xFFFF6D00),
            strokeWidth = 1.dp,
        ),
) {
    companion object {
        val Default: PdfSearchHighlightStyle =
            PdfSearchHighlightStyle()
    }
}

/**
 * Fill, outline, and geometry applied to a search-result rectangle.
 */
@Immutable
data class PdfSearchHighlightDecoration(
    val fillColor: Color,
    val strokeColor: Color = Color.Transparent,
    val strokeWidth: Dp = 0.dp,
    val cornerRadius: Dp = 1.dp,
    val padding: Dp = 0.dp,
) {
    init {
        require(strokeWidth >= 0.dp) {
            "strokeWidth must be non-negative"
        }
        require(cornerRadius >= 0.dp) {
            "cornerRadius must be non-negative"
        }
        require(padding >= 0.dp) {
            "padding must be non-negative"
        }
    }
}
