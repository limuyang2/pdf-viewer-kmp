package io.github.limuyang2.pdf.core.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val pdfiumMutex = Mutex()
private val pdfiumDispatcher = Dispatchers.Default.limitedParallelism(1)
private val pdfiumStateLock = Any()

internal actual suspend fun <T> platformPdfiumCall(
    operation: suspend () -> T,
): T =
    withContext(pdfiumDispatcher) {
        pdfiumMutex.lock()
        try {
            operation()
        } finally {
            pdfiumMutex.unlock()
        }
    }

internal actual fun platformPdfiumClose(operation: () -> Unit) {
    runBlocking {
        withContext(pdfiumDispatcher) {
            pdfiumMutex.withLock {
                operation()
            }
        }
    }
}

internal actual fun <T> withPdfiumStateLock(operation: () -> T): T =
    synchronized(pdfiumStateLock, operation)

internal actual fun platformPdfiumBackend(): PdfiumBackend =
    UnavailablePdfiumBackend
