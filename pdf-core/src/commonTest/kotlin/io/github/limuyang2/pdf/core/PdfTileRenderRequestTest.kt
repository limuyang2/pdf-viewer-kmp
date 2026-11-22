package io.github.limuyang2.pdf.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PdfTileRenderRequestTest {
    @Test
    fun rejectsEmptyNegativeAndOutOfCanvasTiles() {
        assertFailsWith<IllegalArgumentException> { PdfPixelRect(0, 0, 0, 10) }
        assertFailsWith<IllegalArgumentException> { PdfPixelRect(-1, 0, 10, 10) }
        assertFailsWith<IllegalArgumentException> {
            PdfTileRenderRequest(PdfPixelSize(10, 10), PdfPixelRect(0, 0, 10, 11))
        }
    }

    @Test
    fun rejectsEmptyPdfRegions() {
        assertFailsWith<IllegalArgumentException> {
            PdfRenderRequest(PdfPixelSize(10, 10), sourceRect = PdfRect(5.0, 2.0, 5.0, 10.0))
        }
        assertFailsWith<IllegalArgumentException> {
            PdfRenderRequest(PdfPixelSize(10, 10), sourceRect = PdfRect(2.0, 5.0, 10.0, 5.0))
        }
    }

    @Test
    fun edgeTileSizeUsesExclusiveBoundsWithoutAllocatingTheVirtualCanvas() {
        val request = PdfTileRenderRequest(
            PdfPixelSize(Int.MAX_VALUE, Int.MAX_VALUE),
            PdfPixelRect(Int.MAX_VALUE - 3, Int.MAX_VALUE - 2, Int.MAX_VALUE, Int.MAX_VALUE),
        )
        assertEquals(3, request.tileBounds.width)
        assertEquals(2, request.tileBounds.height)
    }
}
