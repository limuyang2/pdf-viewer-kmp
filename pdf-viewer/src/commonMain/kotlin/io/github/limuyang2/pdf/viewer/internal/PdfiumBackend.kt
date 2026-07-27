package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfBitmap
import io.github.limuyang2.pdf.viewer.PdfBookmark
import io.github.limuyang2.pdf.viewer.PdfCapabilities
import io.github.limuyang2.pdf.viewer.PdfDocumentInfo
import io.github.limuyang2.pdf.viewer.PdfLink
import io.github.limuyang2.pdf.viewer.PdfMetadata
import io.github.limuyang2.pdf.viewer.PdfPageInfo
import io.github.limuyang2.pdf.viewer.PdfPixelSize
import io.github.limuyang2.pdf.viewer.PdfRenderRequest
import io.github.limuyang2.pdf.viewer.PdfSearchMatch
import io.github.limuyang2.pdf.viewer.PdfSearchOptions
import io.github.limuyang2.pdf.viewer.PdfSource
import io.github.limuyang2.pdf.viewer.PdfTextLayout
import io.github.limuyang2.pdf.viewer.PdfTextRange

/**
 * Semantic boundary implemented by each platform backend.
 *
 * Every function owns all temporary PDFium handles it creates. Only the
 * document handle can outlive an individual call.
 */
internal interface PdfiumBackend {
    val capabilities: PdfCapabilities

    fun initialize()

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
