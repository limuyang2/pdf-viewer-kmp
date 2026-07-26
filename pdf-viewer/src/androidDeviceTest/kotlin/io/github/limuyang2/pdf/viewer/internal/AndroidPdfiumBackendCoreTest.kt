package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfInvalidFormatException
import io.github.limuyang2.pdf.viewer.PdfPixelFormat
import io.github.limuyang2.pdf.viewer.PdfPixelSize
import io.github.limuyang2.pdf.viewer.PdfRenderRequest
import io.github.limuyang2.pdf.viewer.PdfSource
import io.github.limuyang2.pdf.viewer.PdfViewer
import io.github.limuyang2.pdf.viewer.contract.createSinglePageTestPdf
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
            var bitmap: io.github.limuyang2.pdf.viewer.PdfBitmap? = null
            try {
                assertEquals(1, document.pageCount)
                val information = document[0].information()
                assertEquals(200.0, information.size.width)
                assertEquals(300.0, information.size.height)

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
}
