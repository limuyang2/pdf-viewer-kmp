package io.github.limuyang2.pdf.core

/**
 * Capabilities of the active PDFium backend and bundled PDFium build.
 *
 * A capability can be available in PDFium before it is exposed by the current
 * public API version. The current Android, iOS, JVM, and browser backends
 * enable only [text], [search], and [links].
 */
data class PdfCapabilities(
    /** Basic text extraction through [PdfPage.extractText]. */
    val text: Boolean,
    /** Text search through [PdfPage.search]. */
    val search: Boolean,
    /** Document outline through [PdfDocument.bookmarks]. */
    val bookmarks: Boolean,
    /** Link annotations through [PdfPage.links]. */
    val links: Boolean,
    /** Embedded page thumbnails through [PdfPage.thumbnail]. */
    val thumbnails: Boolean,
    /** Loading document data on demand while rendering. */
    val progressiveLoading: Boolean,
    /** Rendering pages incrementally as data arrives. */
    val progressiveRendering: Boolean,
    /** Reading and submitting form fields. */
    val forms: Boolean,
    /** Modifying document content. */
    val editing: Boolean,
    /** Executing document JavaScript. */
    val javascriptExecution: Boolean,
    /** Rendering XFA (XML Forms Architecture) content. */
    val xfa: Boolean,
)
