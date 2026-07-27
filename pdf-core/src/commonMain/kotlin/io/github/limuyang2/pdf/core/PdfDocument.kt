package io.github.limuyang2.pdf.core

import io.github.limuyang2.pdf.core.internal.NativeDocumentHandle
import io.github.limuyang2.pdf.core.internal.PdfDocumentState
import io.github.limuyang2.pdf.core.internal.PdfiumBackend

/**
 * An open PDF document.
 *
 * Closing is idempotent and invalidates every [PdfPage] descriptor created
 * from this document.
 */
class PdfDocument internal constructor(
    internal val state: PdfDocumentState,
) : AutoCloseable {
    public val pageCount: Int
        get() = state.pageCount

    public val isClosed: Boolean
        get() = state.isClosed

    operator fun get(pageIndex: Int): PdfPage {
        state.requireHandle()
        requirePageIndex(pageIndex)
        return PdfPage(this, pageIndex)
    }

    suspend fun information(): PdfDocumentInfo =
        withOpenDocument { backend, document ->
            backend.documentInformation(document)
        }

    suspend fun metadata(): PdfMetadata =
        withOpenDocument { backend, document ->
            backend.metadata(document)
        }

    suspend fun bookmarks(): List<PdfBookmark> =
        withOpenDocument { backend, document ->
            backend.bookmarks(document).toList()
        }

    suspend fun pageLabel(pageIndex: Int): String? {
        requireOpen()
        requirePageIndex(pageIndex)
        return withOpenDocument { backend, document ->
            backend.pageLabel(document, pageIndex)
        }
    }

    override fun close() {
        state.close()
    }

    /**
     * Closes this document without blocking the calling thread while waiting
     * for an active PDFium operation to finish.
     */
    suspend fun closeAndAwait() {
        state.closeAndAwait()
    }

    internal fun requireOpen() {
        state.requireHandle()
    }

    internal fun requirePageIndex(pageIndex: Int) {
        require(pageIndex in 0 until pageCount) {
            "pageIndex must be in 0 until $pageCount, but was $pageIndex"
        }
    }

    internal suspend fun <T> withOpenDocument(
        onCancelled: (T) -> Unit = {},
        operation: (PdfiumBackend, NativeDocumentHandle) -> T,
    ): T =
        io.github.limuyang2.pdf.core.internal.PdfiumOperation.execute(
            onCancelled = onCancelled,
        ) {
            val document = state.requireHandle()
            operation(state.backend, document)
        }
}
