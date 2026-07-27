package io.github.limuyang2.pdf.core

/**
 * A synchronously readable PDF input owned by [PdfViewer.open].
 *
 * Ownership transfers as soon as `open()` is called. The source is closed
 * after an open failure, cancellation, or closure of the resulting document.
 */
public sealed interface PdfSource : AutoCloseable {
    /**
     * A memory-backed PDF source.
     *
     * The document retains [data] until it is closed. Callers must not mutate
     * the array while the document is open.
     */
    public class Bytes(
        public val data: ByteArray,
    ) : PdfSource {
        override fun close() = Unit
    }

    /**
     * A synchronously readable random-access source.
     *
     * Implementations must fill every requested range and must not call back
     * into [PdfViewer] from [read].
     */
    public interface RandomAccess : PdfSource {
        public val size: Long

        fun read(
            offset: Long,
            destination: ByteArray,
            destinationOffset: Int,
            length: Int,
        ): Int
    }
}
