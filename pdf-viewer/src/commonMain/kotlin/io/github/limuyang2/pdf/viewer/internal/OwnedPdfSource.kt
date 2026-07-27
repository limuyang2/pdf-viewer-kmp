package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfClosedException
import io.github.limuyang2.pdf.viewer.PdfSource

internal class OwnedPdfSource(
    source: PdfSource,
) {
    private var source: PdfSource? = source

    fun requireSource(): PdfSource =
        withPdfiumStateLock {
            source ?: throw PdfClosedException("PDF source")
        }

    val retainedSource: PdfSource?
        get() = withPdfiumStateLock { source }

    fun closeAndAttachTo(failure: Throwable?) {
        val current =
            withPdfiumStateLock {
                val retained = source
                source = null
                retained
            } ?: return
        try {
            current.close()
        } catch (cleanupFailure: Throwable) {
            if (failure == null) {
                throw cleanupFailure
            }
            failure.addSuppressed(cleanupFailure)
        }
    }
}
