package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfPixelFormat
import io.github.limuyang2.pdf.viewer.PdfPixelSize
import io.github.limuyang2.pdf.viewer.PdfInvalidFormatException
import io.github.limuyang2.pdf.viewer.PdfRenderRequest
import io.github.limuyang2.pdf.viewer.PdfSource
import io.github.limuyang2.pdf.viewer.PdfViewer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class IosPdfiumBackendCoreTest {
    @Test
    fun opensInspectsRendersAndExtractsText() =
        runTest {
            val document =
                PdfViewer.open(
                    PdfSource.Bytes(createSinglePagePdf("Hello")),
                )
            var bitmap: io.github.limuyang2.pdf.viewer.PdfBitmap? = null
            try {
                assertEquals(1, document.pageCount)
                assertEquals(1, document.information().version?.major)

                val page = document[0]
                val information = page.information()
                assertEquals(200.0, information.size.width)
                assertEquals(300.0, information.size.height)
                assertEquals("Hello", page.extractText())

                val rendered =
                    page.render(
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
                    PdfSource.Bytes(createSinglePagePdf("Recovered")),
                )
            try {
                assertEquals(1, document.pageCount)
            } finally {
                document.close()
            }
        }
}

private fun createSinglePagePdf(text: String): ByteArray {
    require(text.all { it.code in 0x20..0x7E && it != '(' && it != ')' })
    val stream = "BT /F1 18 Tf 20 250 Td ($text) Tj ET"
    val objects =
        listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] " +
                "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length ${stream.length} >>\nstream\n$stream\nendstream",
        )
    val output = StringBuilder("%PDF-1.7\n")
    val offsets = mutableListOf<Int>()
    objects.forEachIndexed { index, body ->
        offsets += output.length
        output.append("${index + 1} 0 obj\n$body\nendobj\n")
    }
    val xrefOffset = output.length
    output.append("xref\n0 ${objects.size + 1}\n")
    output.append("0000000000 65535 f \n")
    offsets.forEach { offset ->
        output.append(offset.toString().padStart(10, '0'))
        output.append(" 00000 n \n")
    }
    output.append(
        "trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n" +
            "startxref\n$xrefOffset\n%%EOF\n",
    )
    return output.toString().encodeToByteArray()
}
