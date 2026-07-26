package io.github.limuyang2.pdf.viewer.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val pdfiumMutex = Mutex()
private val pendingPdfiumCloseOperations = mutableListOf<() -> Unit>()

internal actual suspend fun <T> platformPdfiumCall(
    operation: suspend () -> T,
): T =
    pdfiumMutex.withLock {
        try {
            operation()
        } finally {
            drainPendingPdfiumCloseOperations()
        }
    }

internal actual fun platformPdfiumClose(operation: () -> Unit) {
    if (pdfiumMutex.tryLock()) {
        try {
            operation()
            drainPendingPdfiumCloseOperations()
        } finally {
            pdfiumMutex.unlock()
        }
    } else {
        // JavaScript and Wasm execute this state on one event-loop thread.
        // The current operation drains the queue before releasing the gate.
        pendingPdfiumCloseOperations += operation
    }
}

private fun drainPendingPdfiumCloseOperations() {
    while (pendingPdfiumCloseOperations.isNotEmpty()) {
        pendingPdfiumCloseOperations.removeAt(0).invoke()
    }
}

internal actual fun <T> withPdfiumStateLock(operation: () -> T): T =
    operation()

internal actual fun platformPdfiumBackend(): PdfiumBackend =
    UnavailablePdfiumBackend
