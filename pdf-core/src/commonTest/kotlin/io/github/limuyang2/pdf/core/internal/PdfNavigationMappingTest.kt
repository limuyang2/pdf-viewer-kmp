package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfDestinationView
import io.github.limuyang2.pdf.core.PdfRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PdfNavigationMappingTest {
    @Test
    fun mapsNullableXyzDestinationValues() {
        val destination =
            pdfDestination(
                pageIndex = 2,
                viewMode = PDF_DEST_VIEW_XYZ,
                parameters = listOf(10.0, 20.0, 2.0),
                hasY = true,
                y = 20.0,
            )

        val view = assertIs<PdfDestinationView.Xyz>(destination.view)
        assertNull(view.x)
        assertEquals(20.0, view.y)
        assertNull(view.zoom)
    }

    @Test
    fun mapsFitRectangleDestination() {
        val destination =
            pdfDestination(
                pageIndex = 1,
                viewMode = PDF_DEST_VIEW_FIT_RECTANGLE,
                parameters = listOf(1.0, 2.0, 3.0, 4.0),
            )

        assertEquals(
            PdfDestinationView.FitRectangle(PdfRect(1.0, 2.0, 3.0, 4.0)),
            destination.view,
        )
    }
}
