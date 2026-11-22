package io.github.limuyang2.pdf.core.contract

import io.github.limuyang2.pdf.core.PdfColor
import io.github.limuyang2.pdf.core.PdfPixelRect
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRect
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfRotation
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfTileRenderRequest
import io.github.limuyang2.pdf.core.PdfViewer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Real-backend contract: raw PDF coordinates, nonzero crop origin, all rotation pairs. */
internal suspend fun verifyRegionRenderingContract() {
    for (intrinsic in PdfRotation.entries) {
        val source = createGraphicsTestPdf(
            stream = "1 0 0 rg -50 -50 300 400 re f 0 0 1 rg 20 30 80 120 re f " +
                "0 1 0 RG 3 w 20 30 m 180 270 l S",
            rotation = intrinsic.degrees,
            cropBox = "20 30 180 270",
        )
        PdfViewer.open(PdfSource.Bytes(source)).use { document ->
            val page = document[0]
            for (extra in PdfRotation.entries) {
                val size = PdfPixelSize(161, 243)
                val request = PdfRenderRequest(size, rotation = extra)
                page.render(request).use { full ->
                    page.render(request.copy(sourceRect = PdfRect(20.0, 30.0, 180.0, 270.0))).use { region ->
                        assertPixelsClose(full.copyPixels(), region.copyPixels(), "region $intrinsic/$extra")
                    }
                    // A virtual canvas may be large; its buffer must never be allocated.
                    val fullPixels = full.copyPixels()
                    for (top in 0 until size.height step 61) {
                        for (left in 0 until size.width step 67) {
                            val bounds = PdfPixelRect(left, top, minOf(left + 67, size.width), minOf(top + 61, size.height))
                            page.renderTile(PdfTileRenderRequest(size, bounds, rotation = extra)).use { tile ->
                                assertEquals(bounds.width, tile.width)
                                assertEquals(bounds.height, tile.height)
                                val pixels = tile.copyPixels()
                                for (row in 0 until tile.height) {
                                    for (column in 0 until tile.width * 4) {
                                        val expected = fullPixels[((top + row) * size.width + left) * 4 + column].toInt() and 255
                                        val actual = pixels[row * tile.stride + column].toInt() and 255
                                        assertTrue(kotlin.math.abs(expected - actual) <= 2,
                                            "tile $intrinsic/$extra at $left,$top row $row byte $column: $expected != $actual")
                                    }
                                }
                            }
                        }
                    }
                }
                // This rectangle is fully within the blue quadrant, in raw PDF coordinates.
                page.render(PdfRenderRequest(PdfPixelSize(17, 19), sourceRect = PdfRect(30.0, 100.0, 60.0, 120.0), rotation = extra)).use { region ->
                    val pixels = region.copyPixels()
                    assertTrue(pixels.asList().chunked(4).all { it == listOf((-1).toByte(), 0.toByte(), 0.toByte(), (-1).toByte()) }, "blue region $intrinsic/$extra: ${pixels.take(16)}")
                }
                page.render(PdfRenderRequest(PdfPixelSize(17, 19), sourceRect = PdfRect(-40.0, -40.0, -20.0, -20.0), rotation = extra, backgroundColor = PdfColor.Transparent)).use { outside ->
                    assertTrue(outside.copyPixels().all { it == 0.toByte() }, "outside $intrinsic/$extra")
                }
                // The requested region includes hidden content outside the CropBox.
                val totalRotation = (intrinsic.degrees + extra.degrees) % 360
                val outputSize = if (totalRotation % 180 == 0) PdfPixelSize(200, 300) else PdfPixelSize(300, 200)
                page.render(PdfRenderRequest(outputSize, sourceRect = PdfRect(0.0, 0.0, 200.0, 300.0), rotation = extra,
                    backgroundColor = PdfColor(0xFF123456u))).use { region ->
                    fun pixelAt(x: Int, y: Int): List<Int> {
                        val px = when (totalRotation) { 0 -> x; 90 -> y; 180 -> 200 - x; else -> 300 - y }
                        val py = when (totalRotation) { 0 -> 300 - y; 90 -> x; 180 -> y; else -> 200 - x }
                        return region.copyPixels().drop(py * region.stride + px * 4).take(4).map { it.toInt() and 255 }
                    }
                    assertEquals(listOf(0x56, 0x34, 0x12, 255), pixelAt(10, 150), "crop background $intrinsic/$extra")
                    assertEquals(listOf(255, 0, 0, 255), pixelAt(40, 110), "crop blue $intrinsic/$extra")
                    assertEquals(listOf(0, 0, 255, 255), pixelAt(150, 100), "crop red $intrinsic/$extra")
                }
            }
            page.renderTile(PdfTileRenderRequest(PdfPixelSize(1_000_000, 1_000_000), PdfPixelRect(999_980, 999_980, 1_000_000, 1_000_000))).use {
                assertEquals(20 * 20 * 4, it.copyPixels().size)
            }
        }
    }
    assertFailsWith<IllegalArgumentException> {
        PdfTileRenderRequest(PdfPixelSize(10, 10), PdfPixelRect(0, 0, 11, 10))
    }
}

// Rasterizers can choose different edge coverage after a float matrix composition.
private fun assertPixelsClose(expected: ByteArray, actual: ByteArray, context: String) {
    assertEquals(expected.size, actual.size)
    val differences = expected.indices.map { kotlin.math.abs((expected[it].toInt() and 255) - (actual[it].toInt() and 255)) }
    assertTrue(differences.all { it <= 2 }, "$context max channel error ${differences.maxOrNull()}")
}
