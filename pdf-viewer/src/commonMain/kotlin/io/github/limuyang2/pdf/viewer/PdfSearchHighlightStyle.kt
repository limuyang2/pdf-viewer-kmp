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
    /** Decoration used for every non-selected match. */
    val match: PdfSearchHighlightDecoration = PdfSearchHighlightDecoration(
        fillColor = Color(0x66FFEB3B),
    ),
    /** Decoration used for the currently selected match. */
    val selectedMatch: PdfSearchHighlightDecoration = PdfSearchHighlightDecoration(
        fillColor = Color(0x80FF9800),
        strokeColor = Color(0xFFFF6D00),
        strokeWidth = 1.dp,
    ),
) {
    companion object {
        /** Yellow matches with an orange selected match. */
        val Default: PdfSearchHighlightStyle = PdfSearchHighlightStyle()
    }
}

/**
 * Fill, outline, and geometry applied to a search-result rectangle.
 */
@Immutable
data class PdfSearchHighlightDecoration(
    /** Color painted inside the match rectangle. */
    val fillColor: Color,
    /** Optional outline color. */
    val strokeColor: Color = Color.Transparent,
    /** Outline width; `0.dp` disables the outline. */
    val strokeWidth: Dp = 0.dp,
    /** Radius applied to all rectangle corners. */
    val cornerRadius: Dp = 1.dp,
    /** Space added outside the PDF-reported match bounds. */
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
