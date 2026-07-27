package io.github.limuyang2.pdf.viewer

import androidx.compose.ui.geometry.Offset
import io.github.limuyang2.pdf.core.PdfLink
import io.github.limuyang2.pdf.core.PdfLinkTarget
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPoint
import io.github.limuyang2.pdf.core.PdfQuad
import io.github.limuyang2.pdf.core.PdfRotation
import io.github.limuyang2.pdf.core.PdfSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PdfLinkHitTest {
    @Test
    fun mapsDisplayedPointsForEveryPageRotation() {
        assertPointEquals(
            PdfPoint(20.0, 70.0),
            mapPoint(PdfRotation.Degrees0, Offset(20f, 30f)),
        )
        assertPointEquals(
            PdfPoint(30.0, 20.0),
            mapPoint(PdfRotation.Degrees90, Offset(20f, 30f)),
        )
        assertPointEquals(
            PdfPoint(80.0, 30.0),
            mapPoint(PdfRotation.Degrees180, Offset(20f, 30f)),
        )
        assertPointEquals(
            PdfPoint(70.0, 80.0),
            mapPoint(PdfRotation.Degrees270, Offset(20f, 30f)),
        )
    }

    @Test
    fun findsTopmostLinkContainingTap() {
        val first = link(10.0, 10.0, 40.0, 40.0)
        val second = link(20.0, 20.0, 50.0, 50.0)

        val found =
            findPdfLinkAt(
                links = listOf(first, second),
                position = Offset(25f, 75f),
                displayedWidth = 100f,
                displayedHeight = 100f,
                pageInformation = pageInfo(PdfRotation.Degrees0),
            )

        assertSame(second, found)
    }

    private fun mapPoint(
        rotation: PdfRotation,
        position: Offset,
    ): PdfPoint =
        displayedPointToPdf(
            position = position,
            displayedWidth = 100f,
            displayedHeight = 100f,
            pageInformation = pageInfo(rotation),
        )

    private fun assertPointEquals(
        expected: PdfPoint,
        actual: PdfPoint,
    ) {
        assertEquals(expected.x, actual.x, absoluteTolerance = 0.0001)
        assertEquals(expected.y, actual.y, absoluteTolerance = 0.0001)
    }

    private fun pageInfo(rotation: PdfRotation): PdfPageInfo =
        PdfPageInfo(
            size = PdfSize(100.0, 100.0),
            rotation = rotation,
            boundingBox = null,
        )

    private fun link(
        left: Double,
        bottom: Double,
        right: Double,
        top: Double,
    ): PdfLink =
        PdfLink(
            bounds =
                listOf(
                    PdfQuad(
                        PdfPoint(left, top),
                        PdfPoint(right, top),
                        PdfPoint(left, bottom),
                        PdfPoint(right, bottom),
                    ),
                ),
            target = PdfLinkTarget.Unsupported(0),
        )
}
