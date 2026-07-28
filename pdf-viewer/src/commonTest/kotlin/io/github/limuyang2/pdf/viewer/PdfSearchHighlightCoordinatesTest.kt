package io.github.limuyang2.pdf.viewer

import androidx.compose.ui.geometry.Rect
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfRect
import io.github.limuyang2.pdf.core.PdfRotation
import io.github.limuyang2.pdf.core.PdfSearchMatch
import io.github.limuyang2.pdf.core.PdfSize
import io.github.limuyang2.pdf.core.PdfTextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

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

    @Test
    fun calculatesVerticalAndHorizontalSearchScrollOffsets() {
        val result =
            searchResult(
                PdfRect(
                    left = 20.0,
                    bottom = 10.0,
                    right = 60.0,
                    top = 30.0,
                ),
            )

        val target =
            assertNotNull(
                calculateSearchScrollTarget(
                    result = result,
                    pageInformation =
                        pageInfo(PdfRotation.Degrees0),
                    metrics =
                        PdfViewLayoutMetrics(
                            viewportWidth = 300,
                            viewportHeight = 100,
                            displayedPageWidth = 400,
                            pagePadding = 10,
                        ),
                    alignment = PdfSearchScrollAlignment.Default,
                ),
            )

        assertEquals(120, target.verticalScrollOffset)
        assertEquals(-60, target.horizontalScrollOffset)
        assertEquals(160f, target.matchCenterY)
    }

    @Test
    fun combinesEveryRectangleInMultilineSearchMatch() {
        val result =
            searchResult(
                PdfRect(20.0, 70.0, 60.0, 90.0),
                PdfRect(20.0, 40.0, 100.0, 60.0),
            )

        val target =
            assertNotNull(
                calculateSearchScrollTarget(
                    result = result,
                    pageInformation =
                        pageInfo(PdfRotation.Degrees0),
                    metrics =
                        PdfViewLayoutMetrics(
                            viewportWidth = 200,
                            viewportHeight = 100,
                            displayedPageWidth = 200,
                            pagePadding = 0,
                        ),
                    alignment = PdfSearchScrollAlignment.Center,
                ),
            )

        assertEquals(-15, target.verticalScrollOffset)
        assertEquals(-40, target.horizontalScrollOffset)
        assertEquals(35f, target.matchCenterY)
    }

    @Test
    fun searchScrollAlignmentRequiresViewportFractions() {
        assertFailsWith<IllegalArgumentException> {
            PdfSearchScrollAlignment(verticalFraction = -0.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PdfSearchScrollAlignment(horizontalFraction = 1.1f)
        }
        assertFailsWith<IllegalArgumentException> {
            PdfSearchScrollAlignment(verticalFraction = Float.NaN)
        }
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
            pageInformation = pageInfo(rotation),
        )
    }

    private fun pageInfo(rotation: PdfRotation): PdfPageInfo {
        val pageSize =
            when (rotation) {
                PdfRotation.Degrees0,
                PdfRotation.Degrees180,
                -> PdfSize(width = 200.0, height = 100.0)
                PdfRotation.Degrees90,
                PdfRotation.Degrees270,
                -> PdfSize(width = 100.0, height = 200.0)
            }
        return PdfPageInfo(
            size = pageSize,
            rotation = rotation,
            boundingBox = null,
        )
    }

    private fun searchResult(
        vararg bounds: PdfRect,
    ): PdfViewSearchResult =
        PdfViewSearchResult(
            pageIndex = 0,
            match =
                PdfSearchMatch(
                    range =
                        PdfTextRange(
                            startCharacterIndex = 0,
                            characterCount = 4,
                        ),
                    bounds = bounds.toList(),
                ),
        )

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
