package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfClosedException
import io.github.limuyang2.pdf.viewer.PdfInvalidFormatException
import io.github.limuyang2.pdf.viewer.PdfSource
import io.github.limuyang2.pdf.viewer.PdfViewer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PdfDocumentLifecycleTest {
    @Test
    fun successfulOpenRetainsSourceAndCapturesPageCount() =
        runTest {
            withFakeBackend { backend ->
                val source = PdfSource.Bytes(byteArrayOf(1, 2, 3))

                val document = PdfViewer.open(source, password = "secret")

                assertEquals(1, backend.openCount)
                assertSame(source, backend.lastSource)
                assertEquals("secret", backend.lastPassword)
                assertEquals(3, document.pageCount)
                assertFalse(document.isClosed)
                assertSame(source, document.state.retainedSource)

                document.close()

                assertTrue(document.isClosed)
                assertNull(document.state.retainedSource)
            }
        }

    @Test
    fun openFailureIsPropagated() =
        runTest {
            val failure = PdfInvalidFormatException()
            val backend = FakePdfiumBackend().apply { openFailure = failure }

            withFakeBackend(backend) {
                val thrown =
                    assertFailsWith<PdfInvalidFormatException> {
                        PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                    }

                assertSame(failure, thrown)
                assertEquals(0, backend.closeCount)
            }
        }

    @Test
    fun closeIsIdempotentAndReleasesBackendOnce() =
        runTest {
            withFakeBackend { backend ->
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))

                document.close()
                document.close()

                assertEquals(1, backend.closeCount)
                assertEquals(listOf("close:1"), backend.calls)
            }
        }

    @Test
    fun operationsAfterCloseAreRejected() =
        runTest {
            withFakeBackend { backend ->
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                document.close()

                assertFailsWith<PdfClosedException> {
                    document[0]
                }
                assertFailsWith<PdfClosedException> {
                    document.information()
                }

                assertEquals(listOf("close:1"), backend.calls)
            }
        }

    @Test
    fun capabilitiesComeFromInstalledBackend() =
        withFakeBackend { backend ->
            assertSame(backend.capabilities, PdfViewer.capabilities)
        }
}
