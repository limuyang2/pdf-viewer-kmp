package io.github.limuyang2.pdf.viewer

import kotlin.test.Test
import kotlin.test.assertSame

class PdfSourceTest {
    @Test
    fun byteSourceRetainsTheProvidedArray() {
        val bytes = byteArrayOf(1, 2, 3)
        val source = PdfSource.Bytes(bytes)

        assertSame(bytes, source.data)
    }
}
