package io.github.limuyang2.pdf.core

import androidx.compose.runtime.Stable
import io.github.limuyang2.pdf.core.internal.NativeDocumentHandle
import io.github.limuyang2.pdf.core.internal.PdfDocumentState
import io.github.limuyang2.pdf.core.internal.PdfiumBackend

/**
 * An open PDF document.
 *
 * Closing is idempotent and invalidates every [PdfPage] descriptor created
 * from this document. Stop using the document in UI and page operations
 * before closing it. A closed document must never be reused.
 */
@Stable
class PdfDocument internal constructor(
    internal val state: PdfDocumentState,
) : AutoCloseable {
    /** Number of pages. Page indexes are zero-based. */
    val pageCount: Int
        get() = state.pageCount

    /** Whether the document has been closed. */
    val isClosed: Boolean
        get() = state.isClosed

    /** Returns a lightweight descriptor for the page at [pageIndex]. */
    operator fun get(pageIndex: Int): PdfPage {
        state.requireHandle()
        requirePageIndex(pageIndex)
        return PdfPage(this, pageIndex)
    }

    /** Returns structural information: version, permissions, and flags. */
    suspend fun information(): PdfDocumentInfo =
        withOpenDocument { backend, document ->
            backend.documentInformation(document)
        }

    /** Returns the document information dictionary values. */
    suspend fun metadata(): PdfMetadata =
        withOpenDocument { backend, document ->
            backend.metadata(document)
        }

    /**
     * Returns the document outline (bookmark tree).
     *
     * Not implemented by the current backends; always throws
     * [PdfUnsupportedFeatureException].
     */
    suspend fun bookmarks(): List<PdfBookmark> =
        withOpenDocument { backend, document ->
            backend.bookmarks(document).toList()
        }

    /**
     * Returns the page label assigned by the page numbering tree, or `null`
     * when the page has no label.
     */
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
