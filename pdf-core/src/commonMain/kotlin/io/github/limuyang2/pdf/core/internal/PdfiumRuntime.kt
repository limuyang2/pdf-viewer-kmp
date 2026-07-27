package io.github.limuyang2.pdf.core.internal

/**
 * Reference counting for PDFium's process-global initialization.
 *
 * All methods are called while [PdfiumCallGate] is held.
 */
internal object PdfiumRuntime {
    private var activeBackend: PdfiumBackend? = null
    private var referenceCount: Int = 0

    suspend fun acquire(backend: PdfiumBackend) {
        val active = activeBackend
        check(active == null || active === backend) {
            "Cannot replace the PDFium backend while documents are open"
        }

        if (referenceCount == 0) {
            backend.initialize()
            activeBackend = backend
        }
        referenceCount += 1
    }

    fun release(backend: PdfiumBackend) {
        check(referenceCount > 0) {
            "PDFium runtime release without a matching acquire"
        }
        check(activeBackend === backend) {
            "PDFium runtime released by a different backend"
        }

        referenceCount -= 1
        if (referenceCount == 0) {
            try {
                backend.destroy()
            } finally {
                activeBackend = null
            }
        }
    }

    internal val referencesForTesting: Int
        get() = referenceCount
}
