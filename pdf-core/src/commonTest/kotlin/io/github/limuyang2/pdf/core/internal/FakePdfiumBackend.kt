package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfBitmap
import io.github.limuyang2.pdf.core.PdfBookmark
import io.github.limuyang2.pdf.core.PdfCapabilities
import io.github.limuyang2.pdf.core.PdfColor
import io.github.limuyang2.pdf.core.PdfDocumentInfo
import io.github.limuyang2.pdf.core.PdfLink
import io.github.limuyang2.pdf.core.PdfMetadata
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPermissions
import io.github.limuyang2.pdf.core.PdfPixelFormat
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRect
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfRotation
import io.github.limuyang2.pdf.core.PdfSearchMatch
import io.github.limuyang2.pdf.core.PdfSearchOptions
import io.github.limuyang2.pdf.core.PdfSize
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfTextLayout
import io.github.limuyang2.pdf.core.PdfTextRange
import io.github.limuyang2.pdf.core.PdfVersion

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
    var closeFailure: Throwable? = null
    var initializeFailure: Throwable? = null
    var initializeBlock: (suspend () -> Unit)? = null
    var initializeCount: Int = 0
    var destroyCount: Int = 0
    var openCount: Int = 0
    var closeCount: Int = 0
    var lastSource: PdfSource? = null
    var lastPassword: String? = null
    val calls: MutableList<String> = mutableListOf()
    var openOperation: () -> Unit = {}
    var pageInformationOperation: () -> Unit = {}
    var activeOperationCount: Int = 0
        private set
    var maximumActiveOperationCount: Int = 0
        private set

    override suspend fun initialize() {
        initializeCount += 1
        initializeBlock?.invoke()
        initializeFailure?.let { throw it }
    }

    override fun destroy() {
        destroyCount += 1
    }

    override fun open(
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
        closeFailure?.let { throw it }
    }

    override fun documentInformation(
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

    override fun metadata(document: NativeDocumentHandle): PdfMetadata {
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

    override fun bookmarks(
        document: NativeDocumentHandle,
    ): List<PdfBookmark> {
        calls += "bookmarks"
        return emptyList()
    }

    override fun pageLabel(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): String {
        calls += "pageLabel:$pageIndex"
        return "Page ${pageIndex + 1}"
    }

    override fun pageInformation(
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

    override fun render(
        document: NativeDocumentHandle,
        pageIndex: Int,
        request: PdfRenderRequest,
    ): PdfBitmap {
        calls += "render:$pageIndex:${request.outputSize.width}x${request.outputSize.height}"
        return FakePdfBitmap(request.outputSize, request.backgroundColor)
    }

    override fun thumbnail(
        document: NativeDocumentHandle,
        pageIndex: Int,
        maximumSize: PdfPixelSize,
    ): PdfBitmap {
        calls += "thumbnail:$pageIndex:${maximumSize.width}x${maximumSize.height}"
        return FakePdfBitmap(maximumSize)
    }

    override fun extractText(
        document: NativeDocumentHandle,
        pageIndex: Int,
        range: PdfTextRange?,
    ): String {
        calls += "extractText:$pageIndex:${range?.startCharacterIndex}:${range?.characterCount}"
        return "fake text"
    }

    override fun textLayout(
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

    override fun search(
        document: NativeDocumentHandle,
        pageIndex: Int,
        query: String,
        options: PdfSearchOptions,
    ): List<PdfSearchMatch> {
        calls += "search:$pageIndex:$query"
        return emptyList()
    }

    override fun links(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): List<PdfLink> {
        calls += "links:$pageIndex"
        return emptyList()
    }
}

internal class FakePdfBitmap(
    size: PdfPixelSize,
    backgroundColor: PdfColor = PdfColor.Transparent,
) : PdfBitmap {
    override val width: Int = size.width
    override val height: Int = size.height
    override val stride: Int = width * PdfPixelFormat.Bgra8888.bytesPerPixel
    override val format: PdfPixelFormat = PdfPixelFormat.Bgra8888
    override var isClosed: Boolean = false
        private set

    private val pixels =
        ByteArray(stride * height).also { buffer ->
            for (offset in buffer.indices step PdfPixelFormat.Bgra8888.bytesPerPixel) {
                buffer[offset] = backgroundColor.blue.toByte()
                buffer[offset + 1] = backgroundColor.green.toByte()
                buffer[offset + 2] = backgroundColor.red.toByte()
                buffer[offset + 3] = backgroundColor.alpha.toByte()
            }
        }

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
