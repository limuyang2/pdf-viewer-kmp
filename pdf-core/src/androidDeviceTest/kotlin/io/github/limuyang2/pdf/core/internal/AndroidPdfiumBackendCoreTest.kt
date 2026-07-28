package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfInvalidFormatException
import io.github.limuyang2.pdf.core.PdfPixelFormat
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import io.github.limuyang2.pdf.core.contract.SearchContractText
import io.github.limuyang2.pdf.core.contract.createSinglePageTestPdf
import io.github.limuyang2.pdf.core.contract.verifySearchContract
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AndroidPdfiumBackendCoreTest {
    @Test
    fun opensInspectsRendersAndCloses() =
        runTest {
            val document =
                PdfViewer.open(
                    PdfSource.Bytes(createSinglePageTestPdf("Android")),
                )
            var bitmap: io.github.limuyang2.pdf.core.PdfBitmap? = null
            try {
                assertEquals(1, document.pageCount)
                assertEquals(1, document.information().version?.major)
                val information = document[0].information()
                assertEquals(200.0, information.size.width)
                assertEquals(300.0, information.size.height)
                assertEquals("Android", document[0].extractText())

                val rendered =
                    document[0].render(
                        PdfRenderRequest(PdfPixelSize(20, 30)),
                    )
                bitmap = rendered
                assertEquals(PdfPixelFormat.Bgra8888, rendered.format)
                assertEquals(80, rendered.stride)
                val pixels = rendered.copyPixels()
                assertEquals(2_400, pixels.size)
                assertContentEquals(
                    byteArrayOf(-1, -1, -1, -1),
                    pixels.copyOfRange(0, 4),
                )

                document.close()
                assertFalse(rendered.isClosed)
                assertContentEquals(pixels, rendered.copyPixels())
            } finally {
                document.close()
                bitmap?.close()
            }
            assertTrue(bitmap.isClosed)
        }

    @Test
    fun failedOpenReleasesRuntimeAndAllowsTheNextOpen() =
        runTest {
            assertFailsWith<PdfInvalidFormatException> {
                PdfViewer.open(PdfSource.Bytes("%PDF-broken".encodeToByteArray()))
            }

            val document =
                PdfViewer.open(
                    PdfSource.Bytes(createSinglePageTestPdf("Recovered")),
                )
            try {
                assertEquals(1, document.pageCount)
            } finally {
                document.close()
            }
        }

    @Test
    fun searchesTextAndReturnsPdfBounds() =
        runTest {
            val document =
                PdfViewer.open(
                    PdfSource.Bytes(
                        createSinglePageTestPdf(SearchContractText),
                    ),
                )
            try {
                assertTrue(PdfViewer.capabilities.search)
                verifySearchContract(document[0])
            } finally {
                document.close()
            }
        }
}
