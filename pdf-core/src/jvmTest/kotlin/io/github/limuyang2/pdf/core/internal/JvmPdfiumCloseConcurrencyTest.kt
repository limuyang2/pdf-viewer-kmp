package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JvmPdfiumCloseConcurrencyTest {
    @Test
    fun closeCallersWaitForActiveOperationAndCleanupExactlyOnce() =
        runTest {
            withFakeBackend { backend ->
                val entered = CompletableDeferred<Unit>()
                val allowCompletion = CountDownLatch(1)
                backend.pageInformationOperation = {
                    entered.complete(Unit)
                    allowCompletion.await()
                }
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val operation = async(Dispatchers.Default) { document[0].information() }
                entered.await()

                val blockingClose = async(Dispatchers.Default) { document.close() }
                val suspendingClose = async(Dispatchers.Default) { document.closeAndAwait() }

                delay(25)
                assertFalse(blockingClose.isCompleted)
                assertFalse(suspendingClose.isCompleted)
                assertEquals(0, backend.closeCount)

                allowCompletion.countDown()
                operation.await()
                blockingClose.await()
                suspendingClose.await()

                assertTrue(document.isClosed)
                assertEquals(1, backend.closeCount)
                assertEquals(1, backend.destroyCount)
            }
        }

    @Test
    fun cancellationAfterBackendEntryDiscardsTheCompletedOperation() =
        runTest {
            withFakeBackend { backend ->
                val entered = CompletableDeferred<Unit>()
                val allowCompletion = CountDownLatch(1)
                backend.pageInformationOperation = {
                    entered.complete(Unit)
                    allowCompletion.await()
                }
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val operation = async(Dispatchers.Default) { document[0].information() }
                entered.await()

                operation.cancel()
                allowCompletion.countDown()

                assertFailsWith<kotlinx.coroutines.CancellationException> {
                    operation.await()
                }
                assertEquals(1, backend.calls.count { it == "pageInformation:0" })
                document.close()
            }
        }

    @Test
    fun cancelledOpenClosesCompletedHandleRuntimeAndSource() =
        runTest {
            withFakeBackend { backend ->
                val source = JvmTrackingPdfSource()
                val entered = CompletableDeferred<Unit>()
                val allowCompletion = CountDownLatch(1)
                backend.openOperation = {
                    entered.complete(Unit)
                    allowCompletion.await()
                }
                val opening = async(Dispatchers.Default) { PdfViewer.open(source) }
                entered.await()

                opening.cancel()
                allowCompletion.countDown()

                assertFailsWith<kotlinx.coroutines.CancellationException> {
                    opening.await()
                }
                assertEquals(1, backend.closeCount)
                assertEquals(1, backend.destroyCount)
                assertEquals(0, PdfiumRuntime.referencesForTesting)
                assertEquals(1, source.closeCount)
            }
        }
}

private class JvmTrackingPdfSource : PdfSource.RandomAccess {
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
