package io.github.limuyang2.pdf.core

import io.github.limuyang2.pdf.core.internal.PdfDocumentState
import io.github.limuyang2.pdf.core.internal.OwnedPdfSource
import io.github.limuyang2.pdf.core.internal.PdfiumOperation
import io.github.limuyang2.pdf.core.internal.PdfiumBackendProvider
import io.github.limuyang2.pdf.core.internal.PdfiumRuntime

/**
 * Process-wide entry point for opening PDF documents.
 */
public object PdfViewer {
    public val capabilities: PdfCapabilities
        get() = PdfiumBackendProvider.backend.capabilities

    /**
     * Opens [source] and takes ownership of it immediately.
     *
     * The source is closed after failure, cancellation, or closure of the
     * returned document.
     */
    public suspend fun open(
        source: PdfSource,
        password: String? = null,
    ): PdfDocument {
        val backend = PdfiumBackendProvider.backend
        val ownedSource = OwnedPdfSource(source)
        try {
            val openedDocument =
                PdfiumOperation.execute(
                    onCancelled = { opened ->
                        var failure: Throwable? = null
                        try {
                            backend.close(opened.handle)
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
                        ownedSource.closeAndAttachTo(failure)
                        failure?.let { throw it }
                    },
                ) {
                    PdfiumRuntime.acquire(backend)
                    try {
                        backend.open(ownedSource.requireSource(), password)
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
                    ownedSource = ownedSource,
                ),
            )
        } catch (failure: Throwable) {
            ownedSource.closeAndAttachTo(failure)
            throw failure
        }
    }
}
