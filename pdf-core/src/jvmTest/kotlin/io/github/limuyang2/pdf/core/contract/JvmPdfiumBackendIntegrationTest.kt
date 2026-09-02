package io.github.limuyang2.pdf.core.contract

import io.github.limuyang2.pdf.core.PdfColor
import io.github.limuyang2.pdf.core.PdfIncorrectPasswordException
import io.github.limuyang2.pdf.core.PdfLinkTarget
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfRotation
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
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
    fun rendersAsymmetricBackgroundInBgraOrder() =
        runTest {
            PdfViewer.open(PdfSource.Bytes(createSinglePageTestPdf(""))).use { document ->
                document[0]
                    .render(
                        PdfRenderRequest(
                            outputSize = PdfPixelSize(8, 8),
                            backgroundColor = PdfColor(0xFF123456u),
                        ),
                    ).use { bitmap ->
                        assertBgraPixel(
                            pixels = bitmap.copyPixels(),
                            offset = 0,
                            blue = 0x56,
                            green = 0x34,
                            red = 0x12,
                            alpha = 0xFF,
                        )
                    }
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

    @Test
    fun readsInternalAndUriLinks() =
        runTest {
            PdfViewer.open(source(PdfContractFixture.InternalLinks)).use { document ->
                val links = document[0].links()
                assertEquals(2, links.size)
                assertEquals(
                    0,
                    assertIs<PdfLinkTarget.Internal>(links[0].target)
                        .destination.pageIndex,
                )
                assertTrue(links[0].bounds.isNotEmpty())
            }
            PdfViewer.open(source(PdfContractFixture.UriLinks)).use { document ->
                val link = document[0].links().single()
                assertEquals(
                    "https://example.com/page.html",
                    assertIs<PdfLinkTarget.Uri>(link.target).uri,
                )
                assertTrue(link.bounds.isNotEmpty())
            }
        }

    @Test
    fun searchesTextAndReturnsPdfBounds() =
        runTest {
            val source =
                PdfSource.Bytes(createSinglePageTestPdf(SearchContractText))
            PdfViewer.open(source).use { document ->
                assertTrue(PdfViewer.capabilities.search)
                verifySearchContract(document[0])
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

private fun assertBgraPixel(
    pixels: ByteArray,
    offset: Int,
    blue: Int,
    green: Int,
    red: Int,
    alpha: Int,
) {
    assertEquals(blue, pixels[offset].toInt() and 0xFF)
    assertEquals(green, pixels[offset + 1].toInt() and 0xFF)
    assertEquals(red, pixels[offset + 2].toInt() and 0xFF)
    assertEquals(alpha, pixels[offset + 3].toInt() and 0xFF)
}
