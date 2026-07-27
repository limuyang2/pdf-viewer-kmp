package io.github.limuyang2.pdf.core.internal

import kotlinx.coroutines.sync.Mutex

private val pdfiumMutex = Mutex()

internal actual suspend fun <T> platformPdfiumCall(
    operation: suspend () -> T,
): T =
    run {
        pdfiumMutex.lock()
        try {
            operation()
        } finally {
            pdfiumMutex.unlock()
        }
    }

internal actual fun platformPdfiumClose(operation: () -> Unit) {
    check(pdfiumMutex.tryLock()) {
        "PdfDocument.close() cannot be called reentrantly from a PDFium backend operation"
    }
    try {
        operation()
    } finally {
        pdfiumMutex.unlock()
    }
}

internal actual fun <T> withPdfiumStateLock(operation: () -> T): T =
    operation()

internal actual fun platformPdfiumBackend(): PdfiumBackend =
    UnavailablePdfiumBackend
