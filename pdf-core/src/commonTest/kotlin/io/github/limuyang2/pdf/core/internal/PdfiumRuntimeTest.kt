package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfInvalidFormatException
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PdfiumRuntimeTest {
    @Test
    fun documentsShareRuntimeUntilLastClose() =
        runTest {
            withFakeBackend { backend ->
                val first = PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                val second = PdfViewer.open(PdfSource.Bytes(byteArrayOf(2)))

                assertEquals(1, backend.initializeCount)
                assertEquals(0, backend.destroyCount)
                assertEquals(2, PdfiumRuntime.referencesForTesting)

                first.close()
                assertEquals(0, backend.destroyCount)
                assertEquals(1, PdfiumRuntime.referencesForTesting)

                second.close()
                assertEquals(1, backend.destroyCount)
                assertEquals(0, PdfiumRuntime.referencesForTesting)
            }
        }

    @Test
    fun failedOpenReleasesRuntimeReference() =
        runTest {
            val backend =
                FakePdfiumBackend().apply {
                    openFailure = PdfInvalidFormatException()
                }

            withFakeBackend(backend) {
                assertFailsWith<PdfInvalidFormatException> {
                    PdfViewer.open(PdfSource.Bytes(byteArrayOf(1)))
                }

                assertEquals(1, backend.initializeCount)
                assertEquals(1, backend.destroyCount)
                assertEquals(0, PdfiumRuntime.referencesForTesting)
            }
        }
}
