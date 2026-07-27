package io.github.limuyang2.pdf.viewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PdfDestinationTest {
    @Test
    fun representsEveryPdfiumDestinationViewMode() {
        val views =
            listOf(
                PdfDestinationView.Unknown(nativeViewMode = 0, parameters = emptyList()),
                PdfDestinationView.Xyz(x = 10.0, y = null, zoom = 1.5),
                PdfDestinationView.FitPage,
                PdfDestinationView.FitHorizontally(top = 20.0),
                PdfDestinationView.FitVertically(left = null),
                PdfDestinationView.FitRectangle(
                    PdfRect(left = 1.0, bottom = 2.0, right = 3.0, top = 4.0),
                ),
                PdfDestinationView.FitBoundingBox,
                PdfDestinationView.FitBoundingBoxHorizontally(top = null),
                PdfDestinationView.FitBoundingBoxVertically(left = 12.0),
            )

        assertEquals(9, views.size)
        assertEquals(
            PdfDestination(
                pageIndex = 3,
                view = PdfDestinationView.Xyz(x = null, y = 40.0, zoom = null),
            ),
            PdfDestination(
                pageIndex = 3,
                view = PdfDestinationView.Xyz(x = null, y = 40.0, zoom = null),
            ),
        )
    }

    @Test
    fun rejectsInvalidDestinationParameters() {
        assertFailsWith<IllegalArgumentException> {
            PdfDestinationView.Xyz(x = Double.NaN, y = null, zoom = null)
        }
        assertFailsWith<IllegalArgumentException> {
            PdfDestinationView.Xyz(x = null, y = null, zoom = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            PdfDestinationView.Unknown(
                nativeViewMode = 99,
                parameters = List(5) { it.toDouble() },
            )
        }
    }
}
