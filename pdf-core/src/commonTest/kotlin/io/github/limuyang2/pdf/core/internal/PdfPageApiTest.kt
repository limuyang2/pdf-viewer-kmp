package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfClosedException
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfTextRange
import io.github.limuyang2.pdf.core.PdfViewer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class PdfPageApiTest {
    @Test
    fun pageIndexIsValidatedBeforeDescriptorCreation() =
        runTest {
            withFakeBackend { _ ->
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))

                assertEquals(0, document[0].index)
                assertEquals(2, document[2].index)
                assertFailsWith<IllegalArgumentException> { document[-1] }
                assertFailsWith<IllegalArgumentException> { document[3] }

                document.close()
            }
        }

    @Test
    fun documentAndPageOperationsDelegateToSemanticBackend() =
        runTest {
            withFakeBackend { backend ->
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val page = document[1]

                assertEquals("Fake PDF", document.metadata().title)
                assertEquals("Page 2", document.pageLabel(1))
                assertEquals(612.0, page.information().size.width)
                page.render(PdfRenderRequest(PdfPixelSize(40, 50))).close()
                page.thumbnail(PdfPixelSize(10, 20))?.close()
                assertEquals(
                    "fake text",
                    page.extractText(PdfTextRange(2, 4)),
                )
                assertEquals("fake text", page.textLayout().text)
                assertEquals(emptyList(), page.search("needle"))
                assertEquals(emptyList(), page.links())
                assertEquals(emptyList(), document.bookmarks())
                assertEquals(1, document.information().version?.major)

                assertEquals(
                    listOf(
                        "metadata",
                        "pageLabel:1",
                        "pageInformation:1",
                        "render:1:40x50",
                        "thumbnail:1:10x20",
                        "extractText:1:2:4",
                        "textLayout:1",
                        "search:1:needle",
                        "links:1",
                        "bookmarks",
                        "information",
                    ),
                    backend.calls,
                )

                document.close()
            }
        }

    @Test
    fun pageDescriptorObservesParentClose() =
        runTest {
            withFakeBackend { backend ->
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val page = document[0]
                assertSame(document, page.document)

                document.close()

                assertFailsWith<PdfClosedException> {
                    page.information()
                }
                assertEquals(listOf("close:1"), backend.calls)
            }
        }

    @Test
    fun emptySearchQueryIsRejectedBeforeBackendCall() =
        runTest {
            withFakeBackend { backend ->
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val page = document[0]

                assertFailsWith<IllegalArgumentException> {
                    page.search("")
                }
                assertNull(backend.calls.singleOrNull { it.startsWith("search:") })

                document.close()
            }
        }

    @Test
    fun searchQueryContainingNullIsRejectedBeforeBackendCall() =
        runTest {
            withFakeBackend { backend ->
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val page = document[0]

                assertFailsWith<IllegalArgumentException> {
                    page.search("needle\u0000suffix")
                }
                assertNull(backend.calls.singleOrNull { it.startsWith("search:") })

                document.close()
            }
        }
}
