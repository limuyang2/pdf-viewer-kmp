package io.github.limuyang2.pdf.core

/**
 * PDF file version, for example `1.7`.
 */
data class PdfVersion(
    /** Major version digit. */
    val major: Int,
    /** Minor version digit. */
    val minor: Int,
) {
    init {
        require(major >= 0) { "major must be non-negative" }
        require(minor >= 0) { "minor must be non-negative" }
    }

    override fun toString(): String = "$major.$minor"
}

/**
 * Structural information about a document.
 */
data class PdfDocumentInfo(
    /** PDF version, or `null` when it could not be determined. */
    val version: PdfVersion?,
    /** Operations the document's security handler allows. */
    val permissions: PdfPermissions,
    /** Security handler revision, or `null` for unencrypted documents. */
    val securityRevision: Int?,
    /** Whether the cross-reference table parsed without errors. */
    val hasValidCrossReferenceTable: Boolean,
    /**
     * Whether the document is linearized for fast web view, or `null` when
     * unknown.
     */
    val isLinearized: Boolean?,
)

/**
 * Document information dictionary values.
 *
 * PDF date values are intentionally left unparsed because malformed and
 * partially specified PDF date strings are common.
 */
data class PdfMetadata(
    /** Document title, or `null` when absent. */
    val title: String?,
    /** Document author, or `null` when absent. */
    val author: String?,
    /** Document subject, or `null` when absent. */
    val subject: String?,
    /** Whitespace- or comma-separated keywords, or `null` when absent. */
    val keywords: String?,
    /** Name of the application that created the source document. */
    val creator: String?,
    /** Name of the application that produced the PDF file. */
    val producer: String?,
    /** Raw PDF date string, or `null` when absent. */
    val creationDate: String?,
    /** Raw PDF date string, or `null` when absent. */
    val modificationDate: String?,
    /** Non-standard information dictionary entries. */
    val additional: Map<String, String> = emptyMap(),
)
