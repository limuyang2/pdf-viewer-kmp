package io.github.limuyang2.pdf.viewer.contract

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class PdfContractFixtureResourceTest {
    @Test
    fun everyContractFixtureDecodesToExpectedPdfHeader() {
        PdfContractFixture.entries.forEach { fixture ->
            val resourceName = "/pdf-contract/${fixture.fileName}.base64"
            val encoded =
                checkNotNull(javaClass.getResourceAsStream(resourceName)) {
                    "Missing contract fixture resource $resourceName"
                }.bufferedReader().use { it.readText() }

            val bytes = decodePdfContractFixture(encoded)
            assertTrue(bytes.size > 8, "$resourceName is unexpectedly small")
            assertContentEquals(
                expected = "%PDF-".encodeToByteArray(),
                actual = bytes.copyOfRange(0, 5),
                message = "$resourceName has no PDF header",
            )
        }
    }
}
