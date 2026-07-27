package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfDestination
import io.github.limuyang2.pdf.core.PdfDestinationView
import io.github.limuyang2.pdf.core.PdfRect

internal fun pdfDestination(
    pageIndex: Int,
    viewMode: Int,
    parameters: List<Double>,
    hasX: Boolean = false,
    x: Double = 0.0,
    hasY: Boolean = false,
    y: Double = 0.0,
    hasZoom: Boolean = false,
    zoom: Double = 0.0,
): PdfDestination {
    val view =
        when (viewMode) {
            PDF_DEST_VIEW_XYZ ->
                PdfDestinationView.Xyz(
                    x = x.takeIf { hasX },
                    y = y.takeIf { hasY },
                    zoom = zoom.takeIf { hasZoom && it > 0.0 },
                )
            PDF_DEST_VIEW_FIT -> PdfDestinationView.FitPage
            PDF_DEST_VIEW_FIT_HORIZONTALLY ->
                PdfDestinationView.FitHorizontally(parameters.getOrNull(0))
            PDF_DEST_VIEW_FIT_VERTICALLY ->
                PdfDestinationView.FitVertically(parameters.getOrNull(0))
            PDF_DEST_VIEW_FIT_RECTANGLE ->
                if (parameters.size >= 4) {
                    PdfDestinationView.FitRectangle(
                        PdfRect(
                            left = parameters[0],
                            bottom = parameters[1],
                            right = parameters[2],
                            top = parameters[3],
                        ),
                    )
                } else {
                    PdfDestinationView.Unknown(viewMode, parameters)
                }
            PDF_DEST_VIEW_FIT_BOUNDING_BOX ->
                PdfDestinationView.FitBoundingBox
            PDF_DEST_VIEW_FIT_BOUNDING_BOX_HORIZONTALLY ->
                PdfDestinationView.FitBoundingBoxHorizontally(
                    parameters.getOrNull(0),
                )
            PDF_DEST_VIEW_FIT_BOUNDING_BOX_VERTICALLY ->
                PdfDestinationView.FitBoundingBoxVertically(
                    parameters.getOrNull(0),
                )
            else -> PdfDestinationView.Unknown(viewMode, parameters)
        }
    return PdfDestination(pageIndex, view)
}

internal const val PDF_DEST_VIEW_XYZ: Int = 1
internal const val PDF_DEST_VIEW_FIT: Int = 2
internal const val PDF_DEST_VIEW_FIT_HORIZONTALLY: Int = 3
internal const val PDF_DEST_VIEW_FIT_VERTICALLY: Int = 4
internal const val PDF_DEST_VIEW_FIT_RECTANGLE: Int = 5
internal const val PDF_DEST_VIEW_FIT_BOUNDING_BOX: Int = 6
internal const val PDF_DEST_VIEW_FIT_BOUNDING_BOX_HORIZONTALLY: Int = 7
internal const val PDF_DEST_VIEW_FIT_BOUNDING_BOX_VERTICALLY: Int = 8

internal const val PDF_LINK_TARGET_INTERNAL: Int = 1
internal const val PDF_LINK_TARGET_URI: Int = 2
internal const val PDF_LINK_TARGET_REMOTE_DOCUMENT: Int = 3
internal const val PDF_LINK_TARGET_UNSUPPORTED: Int = 4
