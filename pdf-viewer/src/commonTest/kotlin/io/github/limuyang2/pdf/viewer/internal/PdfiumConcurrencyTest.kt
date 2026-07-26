package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfClosedException
import io.github.limuyang2.pdf.viewer.PdfSource
import io.github.limuyang2.pdf.viewer.PdfViewer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PdfiumConcurrencyTest {
    @Test
    fun globalGateSerializesConcurrentOperations() =
        runTest {
            withFakeBackend { backend ->
                backend.pageInformationOperation = { delay(1) }
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val page = document[0]

                List(100) {
                    async {
                        page.information()
                    }
                }.awaitAll()

                assertEquals(1, backend.maximumActiveOperationCount)
                assertEquals(100, backend.calls.count { it == "pageInformation:0" })
                document.close()
            }
        }

    @Test
    fun closeWaitsForActiveOperationAndClosesExactlyOnce() =
        runTest {
            withFakeBackend { backend ->
                val entered = CompletableDeferred<Unit>()
                val allowCompletion = CompletableDeferred<Unit>()
                backend.pageInformationOperation = {
                    entered.complete(Unit)
                    allowCompletion.await()
                }
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val operation = async(Dispatchers.Default) { document[0].information() }
                entered.await()

                val closeFinished = CompletableDeferred<Unit>()
                val closeJob =
                    launch(Dispatchers.Default) {
                        document.close()
                        closeFinished.complete(Unit)
                    }

                delay(10)
                assertFalse(closeFinished.isCompleted)
                assertEquals(0, backend.closeCount)

                allowCompletion.complete(Unit)
                operation.await()
                closeJob.join()

                assertTrue(closeFinished.isCompleted)
                assertEquals(1, backend.closeCount)
                assertEquals(1, backend.destroyCount)
            }
        }

    @Test
    fun cancellationWhileWaitingForGatePreventsBackendEntry() =
        runTest {
            withFakeBackend { backend ->
                val firstEntered = CompletableDeferred<Unit>()
                val allowFirstCompletion = CompletableDeferred<Unit>()
                backend.pageInformationOperation = {
                    firstEntered.complete(Unit)
                    allowFirstCompletion.await()
                }
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val page = document[0]
                val first = async(Dispatchers.Default) { page.information() }
                firstEntered.await()

                val waiting = launch(Dispatchers.Default) { page.information() }
                delay(10)
                waiting.cancelAndJoin()
                allowFirstCompletion.complete(Unit)
                first.await()

                assertEquals(1, backend.calls.count { it == "pageInformation:0" })
                document.close()
            }
        }

    @Test
    fun cancellationAfterBackendEntryDiscardsCompletedResult() =
        runTest {
            withFakeBackend { backend ->
                val entered = CompletableDeferred<Unit>()
                val allowCompletion = CompletableDeferred<Unit>()
                backend.pageInformationOperation = {
                    entered.complete(Unit)
                    allowCompletion.await()
                }
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val operation = async(Dispatchers.Default) { document[0].information() }
                entered.await()

                operation.cancel()
                allowCompletion.complete(Unit)

                assertFailsWith<kotlinx.coroutines.CancellationException> {
                    withContext(Dispatchers.Default) {
                        operation.await()
                    }
                }
                assertEquals(1, backend.calls.count { it == "pageInformation:0" })
                document.close()
            }
        }

    @Test
    fun cancelledOpenClosesCompletedHandleAndReleasesRuntime() =
        runTest {
            withFakeBackend { backend ->
                val entered = CompletableDeferred<Unit>()
                val allowCompletion = CompletableDeferred<Unit>()
                backend.openOperation = {
                    entered.complete(Unit)
                    allowCompletion.await()
                }

                val opening =
                    async(Dispatchers.Default) {
                        PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                    }
                entered.await()
                opening.cancel()
                allowCompletion.complete(Unit)

                assertFailsWith<kotlinx.coroutines.CancellationException> {
                    opening.await()
                }
                assertEquals(1, backend.closeCount)
                assertEquals(1, backend.destroyCount)
                assertEquals(0, PdfiumRuntime.referencesForTesting)
            }
        }

    @Test
    fun operationQueuedBeforeCloseStillObservesClosedState() =
        runTest {
            withFakeBackend { backend ->
                val entered = CompletableDeferred<Unit>()
                val allowCompletion = CompletableDeferred<Unit>()
                backend.pageInformationOperation = {
                    entered.complete(Unit)
                    allowCompletion.await()
                }
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val page = document[0]
                val active = async(Dispatchers.Default) { page.information() }
                entered.await()
                val queued =
                    async(Dispatchers.Default) {
                        runCatching { page.information() }
                    }

                val close =
                    launch(Dispatchers.Default) {
                        document.close()
                    }
                delay(10)
                allowCompletion.complete(Unit)
                active.await()
                close.join()

                assertIs<PdfClosedException>(queued.await().exceptionOrNull())
                assertEquals(1, backend.closeCount)
            }
        }
}
