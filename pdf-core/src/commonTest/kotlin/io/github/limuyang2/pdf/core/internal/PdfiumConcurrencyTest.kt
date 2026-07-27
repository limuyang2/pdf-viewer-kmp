package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfiumConcurrencyTest {
    @Test
    fun globalGateSerializesConcurrentOperations() =
        runTest {
            withFakeBackend { backend ->
                backend.pageInformationOperation = {
                    repeat(10_000) {
                        // Keep native-style synchronous work active long enough
                        // for JVM workers to contend for the process gate.
                    }
                }
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val page = document[0]

                List(100) {
                    async(Dispatchers.Default) {
                        page.information()
                    }
                }.awaitAll()

                assertEquals(1, backend.maximumActiveOperationCount)
                assertEquals(100, backend.calls.count { it == "pageInformation:0" })
                document.close()
            }
        }

    @Test
    fun closeAndAwaitAndCloseShareExactlyOnceCleanup() =
        runTest {
            withFakeBackend { backend ->
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))

                document.closeAndAwait()
                document.close()
                document.closeAndAwait()

                assertTrue(document.isClosed)
                assertEquals(1, backend.closeCount)
                assertEquals(1, backend.destroyCount)
            }
        }

    @Test
    fun cancellationBeforeOperationStartsPreventsBackendEntry() =
        runTest {
            withFakeBackend { backend ->
                val document = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val operation =
                    launch(start = CoroutineStart.LAZY) {
                        document[0].information()
                    }

                operation.cancelAndJoin()

                assertEquals(0, backend.calls.count { it == "pageInformation:0" })
                document.close()
            }
        }
}
