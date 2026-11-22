package io.github.limuyang2.pdf.viewer

import androidx.compose.ui.geometry.Rect
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPixelRect
import io.github.limuyang2.pdf.core.PdfPixelSize
import kotlin.math.ceil
import kotlin.math.floor

/** Logical tile plus a one-pixel sampling border, on a single full-page grid. */
internal data class PdfPlannedTile(
    val fullPageSize: PdfPixelSize,
    val bounds: PdfPixelRect,
    val renderBounds: PdfPixelRect,
) {
    fun key(pageIndex: Int) = PdfRenderCacheKey(
        pageIndex, renderBounds.width, renderBounds.height,
        fullPageSize = fullPageSize, tileBounds = renderBounds,
    )
}

/** Visible fractions are relative to the laid-out page, independent of render resolution. */
internal fun planPdfTiles(fullPageSize: PdfPixelSize, visible: Rect): List<PdfPlannedTile> {
    val left = visible.left.coerceIn(0f, 1f).toDouble() * fullPageSize.width
    val top = visible.top.coerceIn(0f, 1f).toDouble() * fullPageSize.height
    val right = visible.right.coerceIn(0f, 1f).toDouble() * fullPageSize.width
    val bottom = visible.bottom.coerceIn(0f, 1f).toDouble() * fullPageSize.height
    if (right <= left || bottom <= top) return emptyList()
    val firstColumn = floor(left / TILE_SIZE).toInt()
    val lastColumn = ceil(right / TILE_SIZE).toInt() - 1
    val firstRow = floor(top / TILE_SIZE).toInt()
    val lastRow = ceil(bottom / TILE_SIZE).toInt() - 1
    val tiles = mutableListOf<PdfPlannedTile>()
    for (row in firstRow..lastRow) {
        for (column in firstColumn..lastColumn) {
            val bounds = PdfPixelRect(
                column * TILE_SIZE, row * TILE_SIZE,
                minOf((column.toLong() + 1) * TILE_SIZE, fullPageSize.width.toLong()).toInt(),
                minOf((row.toLong() + 1) * TILE_SIZE, fullPageSize.height.toLong()).toInt(),
            )
            tiles += PdfPlannedTile(fullPageSize, bounds, PdfPixelRect(
                (bounds.left - 1).coerceAtLeast(0), (bounds.top - 1).coerceAtLeast(0),
                minOf(bounds.right.toLong() + 1, fullPageSize.width.toLong()).toInt(),
                minOf(bounds.bottom.toLong() + 1, fullPageSize.height.toLong()).toInt(),
            ))
        }
    }
    // Center-first ordering also applies when a viewport spans several rows.
    val centerX = (left + right) / 2
    val centerY = (top + bottom) / 2
    return tiles.sortedBy {
        val dx = (it.bounds.left.toDouble() + it.bounds.right) / 2 - centerX
        val dy = (it.bounds.top.toDouble() + it.bounds.bottom) / 2 - centerY
        dx * dx + dy * dy
    }
}

/** Half-octave resolution levels, rounded upward; independent of the full-page cap. */
internal fun tileRenderSize(info: PdfPageInfo, displayedWidth: Int): PdfPixelSize {
    var width = 256.0
    while (width < displayedWidth) width *= 1.4142135623730951
    val ratio = info.size.height / info.size.width
    val targetWidth = ceil(width).coerceIn(1.0, Int.MAX_VALUE.toDouble()).toInt()
    val height = ceil(targetWidth * ratio)
    require(height.isFinite() && height in 1.0..Int.MAX_VALUE.toDouble()) { "tile canvas is too large" }
    return PdfPixelSize(targetWidth, height.toInt())
}

private const val TILE_SIZE = 512

internal fun visiblePageFraction(
    horizontalScroll: Int,
    viewportWidth: Int,
    pagePadding: Int,
    displayedPageWidth: Int,
    itemOffset: Int,
    itemSize: Int,
    viewportStart: Int,
    viewportEnd: Int,
): Rect {
    if (displayedPageWidth <= 0 || itemSize <= 0) return Rect.Zero
    return Rect(
        (horizontalScroll.toDouble() - pagePadding).toFloat() / displayedPageWidth,
        (viewportStart.toDouble() - itemOffset).toFloat() / itemSize,
        (horizontalScroll.toDouble() + viewportWidth - pagePadding).toFloat() / displayedPageWidth,
        (viewportEnd.toDouble() - itemOffset).toFloat() / itemSize,
    )
}
