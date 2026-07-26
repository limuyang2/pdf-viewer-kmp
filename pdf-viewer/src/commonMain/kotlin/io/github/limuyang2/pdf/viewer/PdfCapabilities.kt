package io.github.limuyang2.pdf.viewer

/**
 * Capabilities of the active PDFium backend and bundled PDFium build.
 *
 * A capability can be available in PDFium before it is exposed by the current
 * public API version.
 */
public data class PdfCapabilities(
    val text: Boolean,
    val search: Boolean,
    val bookmarks: Boolean,
    val links: Boolean,
    val thumbnails: Boolean,
    val progressiveLoading: Boolean,
    val progressiveRendering: Boolean,
    val forms: Boolean,
    val editing: Boolean,
    val javascriptExecution: Boolean,
    val xfa: Boolean,
)
