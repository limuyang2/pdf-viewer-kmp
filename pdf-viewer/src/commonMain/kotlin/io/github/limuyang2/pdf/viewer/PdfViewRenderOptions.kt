package io.github.limuyang2.pdf.viewer

/** Selects how [PdfView] obtains page images. */
enum class PdfRenderMode {
    /** Uses visible tiles when they improve on the page preview. */
    Auto,
    /** Uses full-page images limited by `maxRenderDimension`. */
    FullPage,
    /** Uses a preview and visible tiles; falls back when tiles are unsupported. */
    Tiled,
}

/** Rendering policy for [PdfView]. Tile sizes and scheduling remain internal. */
data class PdfViewRenderOptions(
    /** Whether to use full-page images or a preview with visible tiles. */
    val mode: PdfRenderMode = PdfRenderMode.Auto,
)
