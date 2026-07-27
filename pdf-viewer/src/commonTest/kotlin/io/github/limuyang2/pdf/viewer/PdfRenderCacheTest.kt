package io.github.limuyang2.pdf.viewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PdfRenderCacheTest {
    @Test
    fun evictsLeastRecentlyUsedEntriesToMeetByteBudget() {
        val cache = PdfRenderCache<String>(maximumByteCount = 10)
        val first = key(pageIndex = 0)
        val second = key(pageIndex = 1)
        val third = key(pageIndex = 2)

        cache.put(first, "first", byteCount = 4)
        cache.put(second, "second", byteCount = 4)
        assertEquals("first", cache.get(first))

        cache.put(third, "third", byteCount = 4)

        assertNull(cache.get(second))
        assertEquals("first", cache.get(first))
        assertEquals("third", cache.get(third))
        assertEquals(8L, cache.byteCount)
    }

    @Test
    fun retainsOneEntryWhenItExceedsBudgetByItself() {
        val cache = PdfRenderCache<String>(maximumByteCount = 10)
        val oversized = key(pageIndex = 0)

        cache.put(oversized, "oversized", byteCount = 12)

        assertEquals("oversized", cache.get(oversized))
        assertEquals(12L, cache.byteCount)
    }

    @Test
    fun replacingEntryUpdatesTrackedByteCount() {
        val cache = PdfRenderCache<String>(maximumByteCount = 10)
        val key = key(pageIndex = 0)

        cache.put(key, "first", byteCount = 8)
        cache.put(key, "replacement", byteCount = 3)

        assertEquals("replacement", cache.get(key))
        assertEquals(3L, cache.byteCount)
    }

    private fun key(pageIndex: Int): PdfRenderCacheKey =
        PdfRenderCacheKey(
            pageIndex = pageIndex,
            width = 1,
            height = 1,
        )
}
