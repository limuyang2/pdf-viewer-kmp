package io.github.limuyang2.pdf.viewer.internal

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal object PdfiumOperation {
    suspend fun <T> execute(
        onCancelled: (T) -> Unit = {},
        operation: () -> T,
    ): T {
        val callerContext = coroutineContext
        callerContext.ensureActive()

        return PdfiumCallGate.call {
            callerContext.ensureActive()
            val completed = operation()
            try {
                callerContext.ensureActive()
            } catch (failure: Throwable) {
                try {
                    onCancelled(completed)
                } catch (cleanupFailure: Throwable) {
                    failure.addSuppressed(cleanupFailure)
                }
                throw failure
            }
            completed
        }
    }

    fun close(operation: () -> Unit) {
        PdfiumCallGate.close(operation)
    }

    suspend fun closeAndAwait(operation: () -> Unit) {
        withContext(NonCancellable) {
            PdfiumCallGate.call(operation)
        }
    }
}
