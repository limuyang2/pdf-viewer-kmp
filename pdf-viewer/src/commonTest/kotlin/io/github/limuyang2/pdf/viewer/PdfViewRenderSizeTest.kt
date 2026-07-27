package io.github.limuyang2.pdf.viewer

import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfRotation
import io.github.limuyang2.pdf.core.PdfSize
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfViewRenderSizeTest {
    @Test
    fun renderSizePreservesPageRatio() {
        val result =
            calculateRenderSize(
                pageInformation =
                    PdfPageInfo(
                        size = PdfSize(612.0, 792.0),
                        rotation = PdfRotation.Degrees0,
                        boundingBox = null,
                    ),
                requestedWidth = 612,
                maximumDimension = 4096,
            )

        assertEquals(612, result.width)
        assertEquals(792, result.height)
    }

    @Test
    fun renderSizeLimitsLongEdge() {
        val result =
            calculateRenderSize(
                pageInformation =
                    PdfPageInfo(
                        size = PdfSize(200.0, 1000.0),
                        rotation = PdfRotation.Degrees0,
                        boundingBox = null,
                    ),
                requestedWidth = 1000,
                maximumDimension = 2048,
            )

        assertEquals(410, result.width)
        assertEquals(2048, result.height)
    }

    @Test
    fun renderWidthUsesStableBuckets() {
        assertEquals(128, quantizeRenderWidth(1, 4096))
        assertEquals(128, quantizeRenderWidth(128, 4096))
        assertEquals(256, quantizeRenderWidth(129, 4096))
        assertEquals(4096, quantizeRenderWidth(5000, 4096))
    }
}
