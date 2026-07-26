package io.github.limuyang2.pdf.viewer

import io.github.limuyang2.pdf.viewer.internal.PdfDocumentState
import io.github.limuyang2.pdf.viewer.internal.PdfiumOperation
import io.github.limuyang2.pdf.viewer.internal.PdfiumBackendProvider
import io.github.limuyang2.pdf.viewer.internal.PdfiumRuntime

/**
 * Process-wide entry point for opening PDF documents.
 */
public object PdfViewer {
    public val capabilities: PdfCapabilities
        get() = PdfiumBackendProvider.backend.capabilities

    /**
     * Opens [source] and retains it until the returned document is closed.
     */
    public suspend fun open(
        source: PdfSource,
        password: String? = null,
    ): PdfDocument {
        val backend = PdfiumBackendProvider.backend
        val openedDocument =
            PdfiumOperation.execute(
                onCancelled = { opened ->
                    try {
                        backend.close(opened.handle)
                    } finally {
                        PdfiumRuntime.release(backend)
                    }
                },
            ) {
                PdfiumRuntime.acquire(backend)
                try {
                    backend.open(source, password)
                } catch (failure: Throwable) {
                    try {
                        PdfiumRuntime.release(backend)
                    } catch (cleanupFailure: Throwable) {
                        failure.addSuppressed(cleanupFailure)
                    }
                    throw failure
                }
            }
        return PdfDocument(
            PdfDocumentState(
                backend = backend,
                openedDocument = openedDocument,
                source = source,
            ),
        )
    }
}
