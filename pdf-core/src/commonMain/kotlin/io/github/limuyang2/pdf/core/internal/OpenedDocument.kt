package io.github.limuyang2.pdf.core.internal

internal data class OpenedDocument(
    val handle: NativeDocumentHandle,
    val pageCount: Int,
) {
    init {
        require(pageCount >= 0) { "pageCount must be non-negative" }
    }
}
