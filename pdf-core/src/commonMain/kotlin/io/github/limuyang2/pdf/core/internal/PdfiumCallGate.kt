package io.github.limuyang2.pdf.core.internal

/**
 * Process-global gate. PDFium documents cannot use independent locks because
 * PDFium declares its API non-thread-safe at process scope.
 */
internal object PdfiumCallGate {
    suspend fun <T> call(operation: suspend () -> T): T =
        platformPdfiumCall(operation)

    fun close(operation: () -> Unit) {
        platformPdfiumClose(operation)
    }
}

internal expect suspend fun <T> platformPdfiumCall(
    operation: suspend () -> T,
): T

internal expect fun platformPdfiumClose(operation: () -> Unit)
