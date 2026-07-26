package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfClosedException
import io.github.limuyang2.pdf.viewer.PdfSource

internal class PdfDocumentState(
    val backend: PdfiumBackend,
    openedDocument: OpenedDocument,
    source: PdfSource,
) {
    val pageCount: Int = openedDocument.pageCount

    private var handle: NativeDocumentHandle? = openedDocument.handle
    private var closeRequested: Boolean = false
    internal var retainedSource: PdfSource? = source
        private set

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
        val shouldClose =
            withPdfiumStateLock {
                if (closeRequested) {
                    false
                } else {
                    closeRequested = true
                    true
                }
            }
        if (!shouldClose) return

        PdfiumOperation.close {
            val document =
                withPdfiumStateLock {
                    val current = handle
                    handle = null
                    current
                } ?: return@close
            try {
                backend.close(document)
            } finally {
                retainedSource = null
                PdfiumRuntime.release(backend)
            }
        }
    }
}
