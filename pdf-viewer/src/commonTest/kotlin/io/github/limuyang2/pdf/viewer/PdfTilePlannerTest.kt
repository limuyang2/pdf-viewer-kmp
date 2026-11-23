package io.github.limuyang2.pdf.viewer

import androidx.compose.ui.geometry.Rect
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPixelRect
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRotation
import io.github.limuyang2.pdf.core.PdfSize
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfTilePlannerTest {
    @Test
    fun viewportMappingAccountsForPaddingScrollAndPartiallyVisiblePages() {
        val fraction = visiblePageFraction(
            horizontalScroll = 520, viewportWidth = 1000, pagePadding = 20,
            displayedPageWidth = 2000, itemOffset = -250, itemSize = 2000,
            viewportStart = -20, viewportEnd = 980,
        )
        assertEquals(Rect(.25f, .115f, .75f, .615f), fraction)
        val firstPage = visiblePageFraction(0, 1000, 20, 960, 0, 1400, -20, 980)
        val tiles = planPdfTiles(PdfPixelSize(960, 1400), firstPage)
        assertTrue(tiles.any { it.bounds.left == 0 && it.bounds.top == 0 })
        assertTrue(tiles.none { it.bounds.top >= 1024 })
    }

    @Test
    fun exactGridBoundaryDoesNotRequestTheNextTile() {
        val tiles = planPdfTiles(PdfPixelSize(1024, 1024), Rect(0f, 0f, .5f, .5f))
        assertEquals(1, tiles.size)
        assertEquals(PdfPixelRect(0, 0, 512, 512), tiles.single().bounds)
        assertEquals(PdfPixelRect(0, 0, 513, 513), tiles.single().renderBounds)
    }

    @Test
    fun clampsOutsideViewportAndKeepsPartialEdgeTiles() {
        val size = PdfPixelSize(1100, 1300)
        val tiles = planPdfTiles(size, Rect(-1f, -1f, 2f, 2f))
        assertEquals(9, tiles.size)
        assertEquals(1100 * 1300, tiles.sumOf { it.bounds.width * it.bounds.height })
        val edge = tiles.single { it.bounds.left == 1024 && it.bounds.top == 1024 }
        assertEquals(PdfPixelRect(1024, 1024, 1100, 1300), edge.bounds)
        assertEquals(PdfPixelRect(1023, 1023, 1100, 1300), edge.renderBounds)
        assertTrue(tiles.all { it.renderBounds.width <= 514 && it.renderBounds.height <= 514 })
    }

    @Test
    fun invisiblePagesHaveNoRequests() {
        val size = PdfPixelSize(1024, 1024)
        assertTrue(planPdfTiles(size, Rect.Zero).isEmpty())
        assertTrue(planPdfTiles(size, Rect(2f, 0f, 3f, 1f)).isEmpty())
        assertTrue(planPdfTiles(size, Rect(0f, -2f, 1f, -1f)).isEmpty())
    }

    @Test
    fun prioritizesCenterAndReusesGridAcrossSmallPans() {
        val size = PdfPixelSize(2048, 2048)
        val first = planPdfTiles(size, Rect(0f, 0f, .7f, .7f))
        assertEquals(PdfPixelRect(512, 512, 1024, 1024), first.first().bounds)
        val second = planPdfTiles(size, Rect(.01f, .01f, .71f, .71f))
        assertEquals(first.map { it.key(0) }.toSet(), second.map { it.key(0) }.toSet())
    }

    @Test
    fun resolutionIsQuantizedAndExceedsTheFullPageCap() {
        val info = PdfPageInfo(PdfSize(200.0, 300.0), PdfRotation.Degrees0, null)
        val first = tileRenderSize(info, 5000)
        assertTrue(first.width >= 5000)
        assertTrue(first.height >= 7500)
        assertEquals(first, tileRenderSize(info, 5001))
        assertTrue(first.height.toDouble() / first.width in 1.5..1.501)
    }

    @Test
    fun rejectsUnrepresentableCanvasBeforeSubmittingNativeWork() {
        val info = PdfPageInfo(PdfSize(1.0, 1e20), PdfRotation.Degrees0, null)
        assertFailsWith<IllegalArgumentException> { tileRenderSize(info, 1000) }
    }

    @Test
    fun tileKeysCannotCollideWithPreviewOrDifferentResolutions() {
        val tile = planPdfTiles(PdfPixelSize(1024, 1024), Rect(0f, 0f, .5f, .5f)).single()
        val key = tile.key(0)
        assertTrue(key != PdfRenderCacheKey(0, key.width, key.height))
        assertTrue(key != key.copy(fullPageSize = PdfPixelSize(2048, 2048)))
        assertTrue(key != tile.key(1))
    }

    @Test
    fun previousLevelStaysUntilPlannedTilesCoverIt() {
        val old = planPdfTiles(PdfPixelSize(1024, 1024), Rect(0f, 0f, .5f, .5f)).single()
        val plan = planPdfTiles(PdfPixelSize(2048, 2048), Rect(0f, 0f, .5f, .5f)).associateBy { it.key(0) }
        val drawn = mutableMapOf(old.key(0) to PdfDrawnTile(old, "old"))
        assertTrue(unneededTileKeys(drawn, plan).isEmpty())
        // Three of four planned tiles leave a quarter of the old tile exposed.
        plan.keys.take(3).forEach { drawn[it] = PdfDrawnTile(plan.getValue(it), "new") }
        assertTrue(unneededTileKeys(drawn, plan).isEmpty())
        plan.keys.forEach { drawn[it] = PdfDrawnTile(plan.getValue(it), "new") }
        assertEquals(setOf(old.key(0)), unneededTileKeys(drawn, plan))
    }

    @Test
    fun tilesWithoutSuccessorsAreDropped() {
        val old = planPdfTiles(PdfPixelSize(1024, 1024), Rect(0f, 0f, .5f, .5f)).single()
        val drawn = mutableMapOf(old.key(0) to PdfDrawnTile(old, "old"))
        // The viewport moved to page area the old tile does not show.
        val elsewhere = planPdfTiles(PdfPixelSize(2048, 2048), Rect(.5f, .5f, 1f, 1f)).associateBy { it.key(0) }
        assertEquals(setOf(old.key(0)), unneededTileKeys(drawn, elsewhere))
        // No planned tiles at all, as when the preview is sharp enough.
        assertEquals(setOf(old.key(0)), unneededTileKeys(drawn, emptyMap()))
    }
}
