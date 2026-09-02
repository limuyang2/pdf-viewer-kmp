package io.github.limuyang2.pdf.core.contract

import io.github.limuyang2.pdf.core.PdfColor
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WebPdfiumBackendIntegrationTest {
    @Test
    fun opensReadsRendersAndClosesPdf() =
        runTest {
            val text = "Browser PDFium"
            val source = PdfSource.Bytes(createSinglePageTestPdf(text))
            val document = PdfViewer.open(source)
            try {
                assertEquals(1, document.pageCount)
                assertEquals(text, document[0].extractText())
                document[0]
                    .render(PdfRenderRequest(PdfPixelSize(24, 32)))
                    .use { bitmap ->
                        assertEquals(24 * 4, bitmap.stride)
                        assertEquals(24 * 32 * 4, bitmap.copyPixels().size)
                    }
            } finally {
                document.closeAndAwait()
            }
        }

    @Test
    fun rendersAsymmetricBackgroundInBgraOrder() =
        runTest {
            val document =
                PdfViewer.open(
                    PdfSource.Bytes(createSinglePageTestPdf("")),
                )
            try {
                document[0]
                    .render(
                        PdfRenderRequest(
                            outputSize = PdfPixelSize(8, 8),
                            backgroundColor = PdfColor(0xFF123456u),
                        ),
                    ).use { bitmap ->
                        assertEquals(
                            listOf(0x56, 0x34, 0x12, 0xFF),
                            bitmap
                                .copyPixels()
                                .take(4)
                                .map { it.toInt() and 0xFF },
                        )
                    }
            } finally {
                document.closeAndAwait()
            }
        }

    @Test
    fun searchesTextAndReturnsPdfBounds() =
        runTest {
            val source =
                PdfSource.Bytes(createSinglePageTestPdf(SearchContractText))
            val document = PdfViewer.open(source)
            try {
                assertTrue(PdfViewer.capabilities.search)
                verifySearchContract(document[0])
            } finally {
                document.closeAndAwait()
            }
        }
}
