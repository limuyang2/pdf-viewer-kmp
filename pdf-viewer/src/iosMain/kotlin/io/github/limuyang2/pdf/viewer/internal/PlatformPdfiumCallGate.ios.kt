package io.github.limuyang2.pdf.viewer.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.Foundation.NSLock

private val pdfiumMutex = Mutex()
private val pdfiumDispatcher = Dispatchers.Default.limitedParallelism(1)
private val pdfiumStateLock = NSLock()

internal actual suspend fun <T> platformPdfiumCall(
    operation: suspend () -> T,
): T =
    withContext(pdfiumDispatcher) {
        pdfiumMutex.withLock {
            operation()
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

internal actual fun <T> withPdfiumStateLock(operation: () -> T): T {
    pdfiumStateLock.lock()
    return try {
        operation()
    } finally {
        pdfiumStateLock.unlock()
    }
}

internal actual fun platformPdfiumBackend(): PdfiumBackend =
    IosPdfiumBackend
