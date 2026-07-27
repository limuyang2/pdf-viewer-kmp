package io.github.limuyang2.pdf.core

data class PdfVersion(
    val major: Int,
    val minor: Int,
) {
    init {
        require(major >= 0) { "major must be non-negative" }
        require(minor >= 0) { "minor must be non-negative" }
    }

    override fun toString(): String = "$major.$minor"
}

data class PdfDocumentInfo(
    val version: PdfVersion?,
    val permissions: PdfPermissions,
    val securityRevision: Int?,
    val hasValidCrossReferenceTable: Boolean,
    val isLinearized: Boolean?,
)

/**
 * Document information dictionary values.
 *
 * PDF date values are intentionally left unparsed because malformed and
 * partially specified PDF date strings are common.
 */
data class PdfMetadata(
    val title: String?,
    val author: String?,
    val subject: String?,
    val keywords: String?,
    val creator: String?,
    val producer: String?,
    val creationDate: String?,
    val modificationDate: String?,
    val additional: Map<String, String> = emptyMap(),
)
