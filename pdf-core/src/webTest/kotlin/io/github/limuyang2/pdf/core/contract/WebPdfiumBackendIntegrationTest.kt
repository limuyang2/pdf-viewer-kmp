package io.github.limuyang2.pdf.core.contract

import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WebPdfiumBackendIntegrationTest {
    @Test
    fun opensReadsRendersAndClosesPdf() =
        runTest {
            val text = "Browser PDFium"
            val source = PdfSource.Bytes(createSinglePageTestPdf(text))
            PdfViewer.open(source).use { document ->
                assertEquals(1, document.pageCount)
                assertEquals(text, document[0].extractText())
                document[0]
                    .render(PdfRenderRequest(PdfPixelSize(24, 32)))
                    .use { bitmap ->
                        assertEquals(24 * 4, bitmap.stride)
                        assertEquals(24 * 32 * 4, bitmap.copyPixels().size)
                    }
            }
        }
}
