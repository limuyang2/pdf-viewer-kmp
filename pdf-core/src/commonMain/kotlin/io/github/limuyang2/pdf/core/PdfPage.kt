package io.github.limuyang2.pdf.core

/**
 * Lightweight page descriptor. It does not keep a native PDFium page open.
 */
public class PdfPage internal constructor(
    public val document: PdfDocument,
    public val index: Int,
) {
    public suspend fun information(): PdfPageInfo =
        document.withOpenDocument { backend, handle ->
            backend.pageInformation(handle, index)
        }

    public suspend fun render(request: PdfRenderRequest): PdfBitmap =
        document.withOpenDocument(onCancelled = PdfBitmap::close) { backend, handle ->
            backend.render(handle, index, request)
        }

    public suspend fun thumbnail(maximumSize: PdfPixelSize): PdfBitmap? =
        document.withOpenDocument(
            onCancelled = { bitmap -> bitmap?.close() },
        ) { backend, handle ->
            backend.thumbnail(handle, index, maximumSize)
        }

    public suspend fun extractText(range: PdfTextRange? = null): String =
        document.withOpenDocument { backend, handle ->
            backend.extractText(handle, index, range)
        }

    public suspend fun textLayout(): PdfTextLayout =
        document.withOpenDocument { backend, handle ->
            backend.textLayout(handle, index)
        }

    public suspend fun search(
        query: String,
        options: PdfSearchOptions = PdfSearchOptions(),
    ): List<PdfSearchMatch> {
        document.requireOpen()
        require(query.isNotEmpty()) { "query must not be empty" }
        return document.withOpenDocument { backend, handle ->
            backend.search(handle, index, query, options).toList()
        }
    }

    public suspend fun links(): List<PdfLink> =
        document.withOpenDocument { backend, handle ->
            backend.links(handle, index).toList()
        }
}
