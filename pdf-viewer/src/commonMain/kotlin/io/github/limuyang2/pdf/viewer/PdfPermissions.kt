package io.github.limuyang2.pdf.viewer

public data class PdfPermissions(
    val canPrint: Boolean,
    val canModify: Boolean,
    val canCopy: Boolean,
    val canAnnotate: Boolean,
    val canFillForms: Boolean,
    val canExtractForAccessibility: Boolean,
    val canAssemble: Boolean,
    val canPrintHighQuality: Boolean,
)
