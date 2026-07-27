package io.github.limuyang2.pdf.core.internal

internal data class WebOpenedDocument(
    val handle: Int,
    val pageCount: Int,
    val errorCode: Int,
)

internal data class WebDocumentInformation(
    val hasVersion: Boolean,
    val version: Int,
    val permissions: UInt,
    val securityRevision: Int,
    val hasValidCrossReferenceTable: Boolean,
)

internal data class WebPageInformation(
    val width: Double,
    val height: Double,
    val rotation: Int,
    val boundingBox: WebPageBoundingBox?,
)

internal data class WebPageBoundingBox(
    val left: Double,
    val bottom: Double,
    val right: Double,
    val top: Double,
)

internal interface WebPdfiumInterop {
    suspend fun initialize()

    fun destroy()

    fun open(
        bytes: ByteArray,
        password: String?,
    ): WebOpenedDocument

    fun close(handle: Int)

    fun documentInformation(handle: Int): WebDocumentInformation

    fun metadata(
        handle: Int,
        tag: String,
    ): String?

    fun pageLabel(
        handle: Int,
        pageIndex: Int,
    ): String?

    fun pageInformation(
        handle: Int,
        pageIndex: Int,
    ): WebPageInformation?

    fun render(
        handle: Int,
        pageIndex: Int,
        width: Int,
        height: Int,
        rotation: Int,
        backgroundArgb: UInt,
        flags: Int,
    ): ByteArray?

    fun extractText(
        handle: Int,
        pageIndex: Int,
        startCharacterIndex: Int,
        characterCount: Int,
    ): String?
}

internal expect val platformWebPdfiumInterop: WebPdfiumInterop
