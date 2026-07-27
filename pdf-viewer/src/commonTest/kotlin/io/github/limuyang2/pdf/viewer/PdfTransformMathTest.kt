package io.github.limuyang2.pdf.viewer

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfTransformMathTest {
    @Test
    fun zoomKeepsCentroidContentPositionFixed() {
        val currentScroll = 200f
        val centroid = 300f
        val zoomChange = 1.2f
        val panChange = 10f

        val delta =
            anchoredScrollDelta(
                currentScroll = currentScroll,
                centroid = centroid,
                zoomChange = zoomChange,
                panChange = panChange,
            )

        val newScroll = currentScroll + delta
        val newCentroid = centroid + panChange
        assertEquals(
            expected = (currentScroll + centroid) * zoomChange,
            actual = newScroll + newCentroid,
            absoluteTolerance = 0.001f,
        )
    }

    @Test
    fun panWithoutZoomMovesScrollInOppositeDirection() {
        assertEquals(
            expected = -25f,
            actual =
                anchoredScrollDelta(
                    currentScroll = 100f,
                    centroid = 300f,
                    zoomChange = 1f,
                    panChange = 25f,
                ),
        )
    }
}
