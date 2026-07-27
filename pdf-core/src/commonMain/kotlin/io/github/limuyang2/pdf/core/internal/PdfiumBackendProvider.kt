package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfCapabilities
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfUnsupportedFeatureException

internal object PdfiumBackendProvider {
    private var installedBackend: PdfiumBackend? = null

    val backend: PdfiumBackend
        get() = installedBackend ?: platformPdfiumBackend()

    /**
     * Temporary injection seam for common contract tests.
     *
     * Platform backends will replace the unavailable backend as their vertical
     * slices are implemented.
     */
    fun installForTesting(backend: PdfiumBackend): () -> Unit {
        val previous = installedBackend
        installedBackend = backend
        return {
            check(installedBackend === backend) {
                "PDFium test backend was replaced before restoration"
            }
            installedBackend = previous
        }
    }
}

internal expect fun platformPdfiumBackend(): PdfiumBackend

internal object UnavailablePdfiumBackend : PdfiumBackend {
    override val capabilities: PdfCapabilities =
        PdfCapabilities(
            text = false,
            search = false,
            bookmarks = false,
            links = false,
            thumbnails = false,
            progressiveLoading = false,
            progressiveRendering = false,
            forms = false,
            editing = false,
            javascriptExecution = false,
            xfa = false,
        )

    override fun initialize() = Unit

    override fun destroy() = Unit

    override fun open(
        source: PdfSource,
        password: String?,
    ): OpenedDocument =
        throw PdfUnsupportedFeatureException(
            "PDFium backend is not installed for this platform",
        )

    override fun close(document: NativeDocumentHandle): Nothing =
        unavailable()

    override fun documentInformation(document: NativeDocumentHandle): Nothing =
        unavailable()

    override fun metadata(document: NativeDocumentHandle): Nothing =
        unavailable()

    override fun bookmarks(document: NativeDocumentHandle): Nothing =
        unavailable()

    override fun pageLabel(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): Nothing = unavailable()

    override fun pageInformation(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): Nothing = unavailable()

    override fun render(
        document: NativeDocumentHandle,
        pageIndex: Int,
        request: io.github.limuyang2.pdf.core.PdfRenderRequest,
    ): Nothing = unavailable()

    override fun thumbnail(
        document: NativeDocumentHandle,
        pageIndex: Int,
        maximumSize: io.github.limuyang2.pdf.core.PdfPixelSize,
    ): Nothing = unavailable()

    override fun extractText(
        document: NativeDocumentHandle,
        pageIndex: Int,
        range: io.github.limuyang2.pdf.core.PdfTextRange?,
    ): Nothing = unavailable()

    override fun textLayout(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): Nothing = unavailable()

    override fun search(
        document: NativeDocumentHandle,
        pageIndex: Int,
        query: String,
        options: io.github.limuyang2.pdf.core.PdfSearchOptions,
    ): Nothing = unavailable()

    override fun links(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): Nothing = unavailable()

    private fun unavailable(): Nothing =
        throw PdfUnsupportedFeatureException(
            "PDFium backend is not installed for this platform",
        )
}
