package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfClosedException

internal class PdfDocumentState(
    val backend: PdfiumBackend,
    openedDocument: OpenedDocument,
    private val ownedSource: OwnedPdfSource,
) {
    val pageCount: Int = openedDocument.pageCount

    private var handle: NativeDocumentHandle? = openedDocument.handle
    private var closeRequested: Boolean = false
    private var closeCompleted: Boolean = false
    private var closeFailure: Throwable? = null
    internal val retainedSource
        get() = ownedSource.retainedSource

    val isClosed: Boolean
        get() = withPdfiumStateLock { closeRequested }

    fun requireHandle(): NativeDocumentHandle =
        withPdfiumStateLock {
            if (closeRequested) {
                throw PdfClosedException("PdfDocument")
            } else {
                checkNotNull(handle) { "Open document has no backend handle" }
            }
        }

    fun close() {
        requestClose()
        PdfiumOperation.close {
            closeWithinGate()
        }
    }

    suspend fun closeAndAwait() {
        requestClose()
        PdfiumOperation.closeAndAwait {
            closeWithinGate()
        }
    }

    private fun requestClose() {
        withPdfiumStateLock {
            closeRequested = true
        }
    }

    private fun closeWithinGate() {
        val previousCompletion =
            withPdfiumStateLock {
                if (closeCompleted) {
                    CloseCompletion(closeFailure)
                } else {
                    null
                }
            }
        if (previousCompletion != null) {
            previousCompletion.failure?.let { throw it }
            return
        }

        val document =
            withPdfiumStateLock {
                val current = handle
                handle = null
                current
            } ?: error("Document close started without a native handle")
        var failure: Throwable? = null
        try {
            backend.close(document)
        } catch (closeFailure: Throwable) {
            failure = closeFailure
        }
        try {
            PdfiumRuntime.release(backend)
        } catch (releaseFailure: Throwable) {
            if (failure == null) {
                failure = releaseFailure
            } else {
                failure.addSuppressed(releaseFailure)
            }
        }
        try {
            ownedSource.closeAndAttachTo(failure)
        } catch (sourceFailure: Throwable) {
            failure = sourceFailure
        }
        withPdfiumStateLock {
            closeFailure = failure
            closeCompleted = true
        }
        failure?.let { throw it }
    }
}

private data class CloseCompletion(
    val failure: Throwable?,
)
