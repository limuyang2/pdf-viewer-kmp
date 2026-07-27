package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfBitmap
import io.github.limuyang2.pdf.core.PdfBookmark
import io.github.limuyang2.pdf.core.PdfCapabilities
import io.github.limuyang2.pdf.core.PdfDocumentInfo
import io.github.limuyang2.pdf.core.PdfLink
import io.github.limuyang2.pdf.core.PdfMetadata
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfSearchMatch
import io.github.limuyang2.pdf.core.PdfSearchOptions
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfTextLayout
import io.github.limuyang2.pdf.core.PdfTextRange

/**
 * Semantic boundary implemented by each platform backend.
 *
 * Every function owns all temporary PDFium handles it creates. Only the
 * document handle can outlive an individual call.
 */
internal interface PdfiumBackend {
    val capabilities: PdfCapabilities

    suspend fun initialize()

    fun destroy()

    fun open(
        source: PdfSource,
        password: String?,
    ): OpenedDocument

    fun close(document: NativeDocumentHandle)

    fun documentInformation(
        document: NativeDocumentHandle,
    ): PdfDocumentInfo

    fun metadata(
        document: NativeDocumentHandle,
    ): PdfMetadata

    fun bookmarks(
        document: NativeDocumentHandle,
    ): List<PdfBookmark>

    fun pageLabel(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): String?

    fun pageInformation(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfPageInfo

    fun render(
        document: NativeDocumentHandle,
        pageIndex: Int,
        request: PdfRenderRequest,
    ): PdfBitmap

    fun thumbnail(
        document: NativeDocumentHandle,
        pageIndex: Int,
        maximumSize: PdfPixelSize,
    ): PdfBitmap?

    fun extractText(
        document: NativeDocumentHandle,
        pageIndex: Int,
        range: PdfTextRange?,
    ): String

    fun textLayout(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfTextLayout

    fun search(
        document: NativeDocumentHandle,
        pageIndex: Int,
        query: String,
        options: PdfSearchOptions,
    ): List<PdfSearchMatch>

    fun links(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): List<PdfLink>
}
