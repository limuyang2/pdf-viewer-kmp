package io.github.limuyang2.pdf.core

/**
 * Operations an encrypted document's security handler allows. Every flag is
 * `true` for unencrypted documents.
 */
data class PdfPermissions(
    /** Printing at any quality. */
    val canPrint: Boolean,
    /** Editing page content beyond annotations and form fields. */
    val canModify: Boolean,
    /** Copying text and graphics out of the document. */
    val canCopy: Boolean,
    /** Adding or modifying text annotations and form fields. */
    val canAnnotate: Boolean,
    /** Filling existing form fields. */
    val canFillForms: Boolean,
    /** Extracting text for accessibility tools. */
    val canExtractForAccessibility: Boolean,
    /** Assembling pages: inserting, rotating, and deleting. */
    val canAssemble: Boolean,
    /** Printing at full resolution rather than a degraded representation. */
    val canPrintHighQuality: Boolean,
)
