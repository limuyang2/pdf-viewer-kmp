package io.github.limuyang2.pdf.core

/**
 * Lightweight page descriptor. It does not keep a native PDFium page open.
 */
class PdfPage internal constructor(
    /** Document this page belongs to. */
    val document: PdfDocument,
    /** Zero-based index of the page. */
    val index: Int,
) {
    /** Returns the page geometry: size, rotation, and bounding box. */
    suspend fun information(): PdfPageInfo =
        document.withOpenDocument { backend, handle ->
            backend.pageInformation(handle, index)
        }

    /** Renders the full page into a new bitmap following [request]. */
    suspend fun render(request: PdfRenderRequest): PdfBitmap =
        document.withOpenDocument(onCancelled = PdfBitmap::close) { backend, handle ->
            backend.render(handle, index, request)
        }

    /**
     * Returns the embedded thumbnail scaled to fit [maximumSize], or `null`
     * when the page has none.
     *
     * Not implemented by the current backends; always throws
     * [PdfUnsupportedFeatureException].
     */
    suspend fun thumbnail(maximumSize: PdfPixelSize): PdfBitmap? =
        document.withOpenDocument(
            onCancelled = { bitmap -> bitmap?.close() },
        ) { backend, handle ->
            backend.thumbnail(handle, index, maximumSize)
        }

    /**
     * Returns the page text, or only the slice described by [range] when it
     * is non-`null`.
     */
    suspend fun extractText(range: PdfTextRange? = null): String =
        document.withOpenDocument { backend, handle ->
            backend.extractText(handle, index, range)
        }

    /**
     * Returns the page text with per-character geometry.
     *
     * Not implemented by the current backends; always throws
     * [PdfUnsupportedFeatureException].
     */
    suspend fun textLayout(): PdfTextLayout =
        document.withOpenDocument { backend, handle ->
            backend.textLayout(handle, index)
        }

    /**
     * Returns every match of [query] on this page, ordered by position.
     *
     * @throws IllegalArgumentException if [query] is empty or contains a null
     * character.
     */
    suspend fun search(
        query: String,
        options: PdfSearchOptions = PdfSearchOptions(),
    ): List<PdfSearchMatch> {
        document.requireOpen()
        require(query.isNotEmpty()) { "query must not be empty" }
        require('\u0000' !in query) {
            "query must not contain a null character"
        }
        return document.withOpenDocument { backend, handle ->
            backend.search(handle, index, query, options).toList()
        }
    }

    /** Returns the link annotations of this page. */
    suspend fun links(): List<PdfLink> =
        document.withOpenDocument { backend, handle ->
            backend.links(handle, index).toList()
        }
}
