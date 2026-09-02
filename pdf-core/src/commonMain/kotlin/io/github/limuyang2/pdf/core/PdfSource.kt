package io.github.limuyang2.pdf.core

/**
 * A synchronously readable PDF input owned by [PdfViewer.open].
 *
 * Ownership transfers as soon as `open()` is called. The source is closed
 * after an open failure, cancellation, or closure of the resulting document.
 */
sealed interface PdfSource : AutoCloseable {
    /**
     * A memory-backed PDF source.
     *
     * The document retains [data] until it is closed. Callers must not mutate
     * the array while the document is open.
     */
    class Bytes(
        /** Document bytes; retained until the document closes. */
        val data: ByteArray,
    ) : PdfSource {
        override fun close() = Unit
    }

    /**
     * A synchronously readable random-access source.
     *
     * Implementations must fill every requested range and must not call back
     * into [PdfViewer] from [read].
     */
    interface RandomAccess : PdfSource {
        /** Total byte length of the source. */
        val size: Long

        /**
         * Reads up to [length] bytes starting at [offset] into
         * [destination] at [destinationOffset] and returns how many bytes
         * were read.
         */
        fun read(
            offset: Long,
            destination: ByteArray,
            destinationOffset: Int,
            length: Int,
        ): Int
    }
}
