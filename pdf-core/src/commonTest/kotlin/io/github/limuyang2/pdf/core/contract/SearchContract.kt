package io.github.limuyang2.pdf.core.contract

import io.github.limuyang2.pdf.core.PdfPage
import io.github.limuyang2.pdf.core.PdfSearchOptions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal const val SearchContractText: String =
    "aaaaaaaaaa Platform platform Platforms"

internal suspend fun verifySearchContract(page: PdfPage) {
    assertEquals(2, page.search("aaaa").size)
    assertEquals(
        7,
        page.search(
            query = "aaaa",
            options = PdfSearchOptions(consecutive = true),
        ).size,
    )
    assertEquals(
        1,
        page.search(
            query = "platform",
            options = PdfSearchOptions(matchCase = true),
        ).size,
    )
    val wholeWordMatches =
        page.search(
            query = "platform",
            options = PdfSearchOptions(matchWholeWord = true),
        )
    assertEquals(2, wholeWordMatches.size)
    wholeWordMatches.forEach { match ->
        assertEquals(8, match.range.characterCount)
        assertTrue(match.range.startCharacterIndex >= 0)
        assertTrue(match.bounds.isNotEmpty())
        match.bounds.forEach { bounds ->
            assertTrue(bounds.left.isFinite())
            assertTrue(bounds.bottom.isFinite())
            assertTrue(bounds.right.isFinite())
            assertTrue(bounds.top.isFinite())
            assertTrue(bounds.left <= bounds.right)
            assertTrue(bounds.bottom <= bounds.top)
        }
    }
    assertEquals(emptyList(), page.search("missing"))
}
