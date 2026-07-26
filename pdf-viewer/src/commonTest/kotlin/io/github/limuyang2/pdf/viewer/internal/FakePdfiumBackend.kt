package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfBitmap
import io.github.limuyang2.pdf.viewer.PdfBookmark
import io.github.limuyang2.pdf.viewer.PdfCapabilities
import io.github.limuyang2.pdf.viewer.PdfDocumentInfo
import io.github.limuyang2.pdf.viewer.PdfLink
import io.github.limuyang2.pdf.viewer.PdfMetadata
import io.github.limuyang2.pdf.viewer.PdfPageInfo
import io.github.limuyang2.pdf.viewer.PdfPermissions
import io.github.limuyang2.pdf.viewer.PdfPixelFormat
import io.github.limuyang2.pdf.viewer.PdfPixelSize
import io.github.limuyang2.pdf.viewer.PdfRect
import io.github.limuyang2.pdf.viewer.PdfRenderRequest
import io.github.limuyang2.pdf.viewer.PdfRotation
import io.github.limuyang2.pdf.viewer.PdfSearchMatch
import io.github.limuyang2.pdf.viewer.PdfSearchOptions
import io.github.limuyang2.pdf.viewer.PdfSize
import io.github.limuyang2.pdf.viewer.PdfSource
import io.github.limuyang2.pdf.viewer.PdfTextLayout
import io.github.limuyang2.pdf.viewer.PdfTextRange
import io.github.limuyang2.pdf.viewer.PdfVersion

internal class FakePdfiumBackend(
    var openedPageCount: Int = 3,
) : PdfiumBackend {
    override val capabilities: PdfCapabilities =
        PdfCapabilities(
            text = true,
            search = true,
            bookmarks = true,
            links = true,
            thumbnails = true,
            progressiveLoading = false,
            progressiveRendering = false,
            forms = false,
            editing = false,
            javascriptExecution = false,
            xfa = false,
        )

    var openFailure: Throwable? = null
    var initializeCount: Int = 0
    var destroyCount: Int = 0
    var openCount: Int = 0
    var closeCount: Int = 0
    var lastSource: PdfSource? = null
    var lastPassword: String? = null
    val calls: MutableList<String> = mutableListOf()
    var openOperation: suspend () -> Unit = {}
    var pageInformationOperation: suspend () -> Unit = {}
    var activeOperationCount: Int = 0
        private set
    var maximumActiveOperationCount: Int = 0
        private set

    override fun initialize() {
        initializeCount += 1
    }

    override fun destroy() {
        destroyCount += 1
    }

    override suspend fun open(
        source: PdfSource,
        password: String?,
    ): OpenedDocument {
        openCount += 1
        lastSource = source
        lastPassword = password
        openOperation()
        openFailure?.let { throw it }
        return OpenedDocument(
            handle = NativeDocumentHandle(openCount.toLong()),
            pageCount = openedPageCount,
        )
    }

    override fun close(document: NativeDocumentHandle) {
        closeCount += 1
        calls += "close:${document.value}"
    }

    override suspend fun documentInformation(
        document: NativeDocumentHandle,
    ): PdfDocumentInfo {
        calls += "information"
        return PdfDocumentInfo(
            version = PdfVersion(1, 7),
            permissions =
                PdfPermissions(
                    canPrint = true,
                    canModify = true,
                    canCopy = true,
                    canAnnotate = true,
                    canFillForms = true,
                    canExtractForAccessibility = true,
                    canAssemble = true,
                    canPrintHighQuality = true,
                ),
            securityRevision = null,
            hasValidCrossReferenceTable = true,
            isLinearized = false,
        )
    }

    override suspend fun metadata(document: NativeDocumentHandle): PdfMetadata {
        calls += "metadata"
        return PdfMetadata(
            title = "Fake PDF",
            author = null,
            subject = null,
            keywords = null,
            creator = null,
            producer = null,
            creationDate = null,
            modificationDate = null,
        )
    }

    override suspend fun bookmarks(
        document: NativeDocumentHandle,
    ): List<PdfBookmark> {
        calls += "bookmarks"
        return emptyList()
    }

    override suspend fun pageLabel(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): String {
        calls += "pageLabel:$pageIndex"
        return "Page ${pageIndex + 1}"
    }

    override suspend fun pageInformation(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfPageInfo {
        calls += "pageInformation:$pageIndex"
        activeOperationCount += 1
        maximumActiveOperationCount =
            maxOf(maximumActiveOperationCount, activeOperationCount)
        return try {
            pageInformationOperation()
            PdfPageInfo(
                size = PdfSize(612.0, 792.0),
                rotation = PdfRotation.Degrees0,
                boundingBox = PdfRect(0.0, 0.0, 612.0, 792.0),
            )
        } finally {
            activeOperationCount -= 1
        }
    }

    override suspend fun render(
        document: NativeDocumentHandle,
        pageIndex: Int,
        request: PdfRenderRequest,
    ): PdfBitmap {
        calls += "render:$pageIndex:${request.outputSize.width}x${request.outputSize.height}"
        return FakePdfBitmap(request.outputSize)
    }

    override suspend fun thumbnail(
        document: NativeDocumentHandle,
        pageIndex: Int,
        maximumSize: PdfPixelSize,
    ): PdfBitmap {
        calls += "thumbnail:$pageIndex:${maximumSize.width}x${maximumSize.height}"
        return FakePdfBitmap(maximumSize)
    }

    override suspend fun extractText(
        document: NativeDocumentHandle,
        pageIndex: Int,
        range: PdfTextRange?,
    ): String {
        calls += "extractText:$pageIndex:${range?.startCharacterIndex}:${range?.characterCount}"
        return "fake text"
    }

    override suspend fun textLayout(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfTextLayout {
        calls += "textLayout:$pageIndex"
        return PdfTextLayout(
            text = "fake text",
            characters = emptyList(),
            rangeBounds = { emptyList() },
        )
    }

    override suspend fun search(
        document: NativeDocumentHandle,
        pageIndex: Int,
        query: String,
        options: PdfSearchOptions,
    ): List<PdfSearchMatch> {
        calls += "search:$pageIndex:$query"
        return emptyList()
    }

    override suspend fun links(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): List<PdfLink> {
        calls += "links:$pageIndex"
        return emptyList()
    }
}

internal class FakePdfBitmap(
    size: PdfPixelSize,
) : PdfBitmap {
    override val width: Int = size.width
    override val height: Int = size.height
    override val stride: Int = width * PdfPixelFormat.Bgra8888.bytesPerPixel
    override val format: PdfPixelFormat = PdfPixelFormat.Bgra8888
    override var isClosed: Boolean = false
        private set

    private val pixels = ByteArray(stride * height)

    override fun copyPixels(): ByteArray {
        check(!isClosed) { "bitmap is closed" }
        return pixels.copyOf()
    }

    override fun copyPixels(
        destination: ByteArray,
        destinationOffset: Int,
    ) {
        check(!isClosed) { "bitmap is closed" }
        pixels.copyInto(destination, destinationOffset)
    }

    override fun close() {
        isClosed = true
    }
}

internal inline fun <T> withFakeBackend(
    backend: FakePdfiumBackend = FakePdfiumBackend(),
    block: (FakePdfiumBackend) -> T,
): T {
    val restore = PdfiumBackendProvider.installForTesting(backend)
    return try {
        block(backend)
    } finally {
        restore()
    }
}
