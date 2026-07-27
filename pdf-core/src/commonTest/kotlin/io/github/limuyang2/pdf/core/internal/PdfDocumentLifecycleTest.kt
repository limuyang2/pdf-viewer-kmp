package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfClosedException
import io.github.limuyang2.pdf.core.PdfInvalidFormatException
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
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
            val source = TrackingPdfSource()

            withFakeBackend(backend) {
                val thrown =
                    assertFailsWith<PdfInvalidFormatException> {
                        PdfViewer.open(source)
                    }

                assertSame(failure, thrown)
                assertEquals(0, backend.closeCount)
                assertEquals(1, source.closeCount)
            }
        }

    @Test
    fun closeIsIdempotentAndReleasesBackendOnce() =
        runTest {
            withFakeBackend { backend ->
                val source = TrackingPdfSource()
                val document = PdfViewer.open(source)

                document.close()
                document.close()

                assertEquals(1, backend.closeCount)
                assertEquals(1, source.closeCount)
                assertEquals(listOf("close:1"), backend.calls)
            }
        }

    @Test
    fun closeAndAwaitCompletesAllCleanupAndRemainsIdempotentWithClose() =
        runTest {
            withFakeBackend { backend ->
                val source = TrackingPdfSource()
                val document = PdfViewer.open(source)

                document.closeAndAwait()
                document.close()

                assertTrue(document.isClosed)
                assertEquals(1, backend.closeCount)
                assertEquals(1, backend.destroyCount)
                assertEquals(1, source.closeCount)
                assertNull(document.state.retainedSource)
            }
        }

    @Test
    fun everyCloseCallerObservesTheSameCleanupFailure() =
        runTest {
            withFakeBackend { backend ->
                val failure = IllegalStateException("close failed")
                backend.closeFailure = failure
                val source = TrackingPdfSource()
                val document = PdfViewer.open(source)

                val first =
                    assertFailsWith<IllegalStateException> {
                        document.close()
                    }
                val second =
                    assertFailsWith<IllegalStateException> {
                        document.closeAndAwait()
                    }

                assertEquals(failure.message, first.message)
                assertEquals(failure.message, second.message)
                assertEquals(1, backend.closeCount)
                assertEquals(1, backend.destroyCount)
                assertEquals(1, source.closeCount)
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

private class TrackingPdfSource : PdfSource.RandomAccess {
    override val size: Long = 1
    var closeCount: Int = 0
        private set

    override fun read(
        offset: Long,
        destination: ByteArray,
        destinationOffset: Int,
        length: Int,
    ): Int {
        destination[destinationOffset] = 1
        return length
    }

    override fun close() {
        closeCount += 1
    }
}

    @Test
    fun capabilitiesComeFromInstalledBackend() =
        withFakeBackend { backend ->
            assertSame(backend.capabilities, PdfViewer.capabilities)
        }
}
