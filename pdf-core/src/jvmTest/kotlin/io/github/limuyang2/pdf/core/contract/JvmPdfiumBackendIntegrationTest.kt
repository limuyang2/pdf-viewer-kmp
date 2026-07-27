package io.github.limuyang2.pdf.core.contract

import io.github.limuyang2.pdf.core.PdfIncorrectPasswordException
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfRotation
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JvmPdfiumBackendIntegrationTest {
    @Test
    fun opensReadsAndClosesDocument() =
        runTest {
            PdfViewer.open(source(PdfContractFixture.PageLabels)).use { document ->
                assertEquals(7, document.pageCount)
                assertEquals("i", document.pageLabel(0))
                assertEquals("", document.pageLabel(6))
                assertTrue(document.information().hasValidCrossReferenceTable)
            }
        }

    @Test
    fun readsPageRendersBgraAndExtractsText() =
        runTest {
            PdfViewer.open(source(PdfContractFixture.RotatedPages)).use { document ->
                val info = document[0].information()
                assertEquals(PdfRotation.Degrees90, info.rotation)
                assertEquals(612.0, info.size.width)
                assertEquals(792.0, info.size.height)
                document[0]
                    .render(PdfRenderRequest(PdfPixelSize(32, 48)))
                    .use { bitmap ->
                        assertEquals(32 * 4, bitmap.stride)
                        assertEquals(32 * 48 * 4, bitmap.copyPixels().size)
                    }
            }
            PdfViewer.open(source(PdfContractFixture.UnicodeText)).use { document ->
                assertEquals(
                    PdfContractExpectations.UnicodeText,
                    document[0].extractText(),
                )
            }
        }

    @Test
    fun mapsIncorrectPassword() =
        runTest {
            assertFailsWith<PdfIncorrectPasswordException> {
                PdfViewer.open(
                    source(PdfContractFixture.EncryptedUserPassword),
                    password = "wrong",
                )
            }
        }

    private fun source(fixture: PdfContractFixture): PdfSource {
        val resourceName = "/pdf-contract/${fixture.fileName}.base64"
        val encoded =
            checkNotNull(javaClass.getResourceAsStream(resourceName)) {
                "Missing contract fixture resource $resourceName"
            }.bufferedReader().use { it.readText() }
        return PdfSource.Bytes(decodePdfContractFixture(encoded))
    }
}
