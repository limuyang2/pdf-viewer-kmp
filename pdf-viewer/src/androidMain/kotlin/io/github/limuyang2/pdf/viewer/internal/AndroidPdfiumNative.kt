package io.github.limuyang2.pdf.viewer.internal

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
}
