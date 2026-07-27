package io.github.limuyang2.pdf.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PdfTextRangeTest {
    @Test
    fun exposesExclusiveEndCharacterIndex() {
        val range = PdfTextRange(startCharacterIndex = 4, characterCount = 7)

        assertEquals(11, range.endCharacterIndexExclusive)
    }

    @Test
    fun rejectsNegativeOrOverflowingRanges() {
        assertFailsWith<IllegalArgumentException> {
            PdfTextRange(startCharacterIndex = -1, characterCount = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            PdfTextRange(startCharacterIndex = 0, characterCount = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            PdfTextRange(
                startCharacterIndex = Int.MAX_VALUE,
                characterCount = 1,
            )
        }
    }
}
