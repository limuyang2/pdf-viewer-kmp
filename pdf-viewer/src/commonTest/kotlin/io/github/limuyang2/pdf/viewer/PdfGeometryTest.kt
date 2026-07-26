package io.github.limuyang2.pdf.viewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PdfGeometryTest {
    @Test
    fun rectangleExposesNormalizedDimensions() {
        val rectangle = PdfRect(left = 1.0, bottom = 2.0, right = 11.0, top = 22.0)

        assertEquals(10.0, rectangle.width)
        assertEquals(20.0, rectangle.height)
    }

    @Test
    fun rectangleRejectsInvertedEdges() {
        assertFailsWith<IllegalArgumentException> {
            PdfRect(left = 2.0, bottom = 0.0, right = 1.0, top = 1.0)
        }
        assertFailsWith<IllegalArgumentException> {
            PdfRect(left = 0.0, bottom = 2.0, right = 1.0, top = 1.0)
        }
    }

    @Test
    fun geometryRejectsNonFiniteValues() {
        assertFailsWith<IllegalArgumentException> {
            PdfPoint(Double.NaN, 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            PdfSize(Double.POSITIVE_INFINITY, 1.0)
        }
    }

    @Test
    fun pixelSizeMustBePositive() {
        assertFailsWith<IllegalArgumentException> {
            PdfPixelSize(0, 100)
        }
        assertFailsWith<IllegalArgumentException> {
            PdfPixelSize(100, -1)
        }
    }

    @Test
    fun rotationOnlyAcceptsQuarterTurns() {
        assertEquals(PdfRotation.Degrees270, PdfRotation.fromDegrees(270))
        assertFailsWith<IllegalArgumentException> {
            PdfRotation.fromDegrees(360)
        }
    }
}
