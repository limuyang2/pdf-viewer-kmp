package io.github.limuyang2.pdf.viewer

public sealed interface PdfSource {
    /**
     * A memory-backed PDF source.
     *
     * The document retains [data] until it is closed. Callers must not mutate
     * the array while the document is open.
     */
    public class Bytes(
        public val data: ByteArray,
    ) : PdfSource

    /**
     * A synchronously readable random-access source.
     *
     * Implementations must fill every requested range and must not call back
     * into [PdfViewer] from [read].
     */
    public interface RandomAccess : PdfSource {
        public val size: Long

        public fun read(
            offset: Long,
            destination: ByteArray,
            destinationOffset: Int,
            length: Int,
        ): Int
    }
}
