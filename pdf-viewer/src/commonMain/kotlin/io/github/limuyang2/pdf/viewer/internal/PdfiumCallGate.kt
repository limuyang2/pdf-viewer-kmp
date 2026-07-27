package io.github.limuyang2.pdf.viewer.internal

/**
 * Process-global gate. PDFium documents cannot use independent locks because
 * PDFium declares its API non-thread-safe at process scope.
 */
internal object PdfiumCallGate {
    suspend fun <T> call(operation: () -> T): T =
        platformPdfiumCall(operation)

    fun close(operation: () -> Unit) {
        platformPdfiumClose(operation)
    }
}

internal expect suspend fun <T> platformPdfiumCall(
    operation: () -> T,
): T

internal expect fun platformPdfiumClose(operation: () -> Unit)
