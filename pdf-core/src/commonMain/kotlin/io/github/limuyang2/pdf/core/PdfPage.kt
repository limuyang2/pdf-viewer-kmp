package io.github.limuyang2.pdf.core

/**
 * Lightweight page descriptor. It does not keep a native PDFium page open.
 */
class PdfPage internal constructor(
    val document: PdfDocument,
    val index: Int,
) {
    suspend fun information(): PdfPageInfo =
        document.withOpenDocument { backend, handle ->
            backend.pageInformation(handle, index)
        }

    suspend fun render(request: PdfRenderRequest): PdfBitmap =
        document.withOpenDocument(onCancelled = PdfBitmap::close) { backend, handle ->
            backend.render(handle, index, request)
        }

    suspend fun thumbnail(maximumSize: PdfPixelSize): PdfBitmap? =
        document.withOpenDocument(
            onCancelled = { bitmap -> bitmap?.close() },
        ) { backend, handle ->
            backend.thumbnail(handle, index, maximumSize)
        }

    suspend fun extractText(range: PdfTextRange? = null): String =
        document.withOpenDocument { backend, handle ->
            backend.extractText(handle, index, range)
        }

    suspend fun textLayout(): PdfTextLayout =
        document.withOpenDocument { backend, handle ->
            backend.textLayout(handle, index)
        }

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

    suspend fun links(): List<PdfLink> =
        document.withOpenDocument { backend, handle ->
            backend.links(handle, index).toList()
        }
}
