package io.github.limuyang2.pdf.viewer

import androidx.compose.ui.geometry.Rect
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfRect
import io.github.limuyang2.pdf.core.PdfRotation
import io.github.limuyang2.pdf.core.PdfSize
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfSearchHighlightCoordinatesTest {
    @Test
    fun mapsSearchBoundsForEveryPageRotation() {
        val bounds =
            PdfRect(
                left = 20.0,
                bottom = 10.0,
                right = 60.0,
                top = 30.0,
            )

        assertRectEquals(
            expected = Rect(40f, 140f, 120f, 180f),
            actual = map(bounds, PdfRotation.Degrees0),
        )
        assertRectEquals(
            expected = Rect(20f, 40f, 60f, 120f),
            actual = map(bounds, PdfRotation.Degrees90),
        )
        assertRectEquals(
            expected = Rect(280f, 20f, 360f, 60f),
            actual = map(bounds, PdfRotation.Degrees180),
        )
        assertRectEquals(
            expected = Rect(140f, 280f, 180f, 360f),
            actual = map(bounds, PdfRotation.Degrees270),
        )
    }

    private fun map(
        bounds: PdfRect,
        rotation: PdfRotation,
    ): Rect {
        val pageSize =
            when (rotation) {
                PdfRotation.Degrees0,
                PdfRotation.Degrees180,
                -> PdfSize(width = 200.0, height = 100.0)
                PdfRotation.Degrees90,
                PdfRotation.Degrees270,
                -> PdfSize(width = 100.0, height = 200.0)
            }
        return pdfRectToDisplayedRect(
            bounds = bounds,
            displayedWidth = pageSize.width.toFloat() * 2f,
            displayedHeight = pageSize.height.toFloat() * 2f,
            pageInformation =
                PdfPageInfo(
                    size = pageSize,
                    rotation = rotation,
                    boundingBox = null,
                ),
        )
    }

    private fun assertRectEquals(
        expected: Rect,
        actual: Rect,
    ) {
        assertEquals(expected.left, actual.left, absoluteTolerance = 0.001f)
        assertEquals(expected.top, actual.top, absoluteTolerance = 0.001f)
        assertEquals(expected.right, actual.right, absoluteTolerance = 0.001f)
        assertEquals(expected.bottom, actual.bottom, absoluteTolerance = 0.001f)
    }
}
