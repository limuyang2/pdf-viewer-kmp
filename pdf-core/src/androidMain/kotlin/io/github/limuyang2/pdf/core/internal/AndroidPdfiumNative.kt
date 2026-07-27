package io.github.limuyang2.pdf.core.internal

internal object AndroidPdfiumNative {
    init {
        System.loadLibrary("pdfviewer_bridge")
    }

    external fun nativeInitialize()

    external fun nativeDestroy()

    external fun nativeOpen(
        data: ByteArray,
        password: String?,
    ): LongArray?

    external fun nativeClose(handle: Long)

    external fun nativeDocumentInformation(handle: Long): LongArray?

    external fun nativeMetadata(
        handle: Long,
        tag: String,
    ): String?

    external fun nativePageLabel(
        handle: Long,
        pageIndex: Int,
    ): String?

    external fun nativePageInformation(
        handle: Long,
        pageIndex: Int,
    ): DoubleArray?

    external fun nativeRender(
        handle: Long,
        pageIndex: Int,
        width: Int,
        height: Int,
        rotation: Int,
        backgroundColor: Long,
        renderAnnotations: Boolean,
        grayscale: Boolean,
        lcdText: Boolean,
    ): ByteArray?

    external fun nativeExtractText(
        handle: Long,
        pageIndex: Int,
        startCharacterIndex: Int,
        characterCount: Int,
    ): String?
}
