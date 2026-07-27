package io.github.limuyang2.pdf.core

data class PdfDestination(
    val pageIndex: Int,
    val view: PdfDestinationView,
) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
    }
}

sealed interface PdfDestinationView {
    data class Unknown(
        val nativeViewMode: Int,
        val parameters: List<Double>,
    ) : PdfDestinationView {
        init {
            require(nativeViewMode >= 0) {
                "nativeViewMode must be non-negative"
            }
            require(parameters.size <= 4) {
                "parameters must contain at most four values"
            }
            require(parameters.all(Double::isFinite)) {
                "parameters must be finite"
            }
        }
    }

    data class Xyz(
        val x: Double?,
        val y: Double?,
        val zoom: Double?,
    ) : PdfDestinationView {
        init {
            require(x == null || x.isFinite()) {
                "x must be null or finite"
            }
            require(y == null || y.isFinite()) {
                "y must be null or finite"
            }
            require(zoom == null || zoom.isFinite() && zoom > 0.0) {
                "zoom must be null or finite and positive"
            }
        }
    }

    data object FitPage : PdfDestinationView

    data class FitHorizontally(
        val top: Double?,
    ) : PdfDestinationView {
        init {
            require(top == null || top.isFinite()) {
                "top must be null or finite"
            }
        }
    }

    data class FitVertically(
        val left: Double?,
    ) : PdfDestinationView {
        init {
            require(left == null || left.isFinite()) {
                "left must be null or finite"
            }
        }
    }

    data class FitRectangle(
        val bounds: PdfRect,
    ) : PdfDestinationView

    data object FitBoundingBox : PdfDestinationView

    data class FitBoundingBoxHorizontally(
        val top: Double?,
    ) : PdfDestinationView {
        init {
            require(top == null || top.isFinite()) {
                "top must be null or finite"
            }
        }
    }

    data class FitBoundingBoxVertically(
        val left: Double?,
    ) : PdfDestinationView {
        init {
            require(left == null || left.isFinite()) {
                "left must be null or finite"
            }
        }
    }
}

sealed interface PdfLinkTarget {
    data class Internal(
        val destination: PdfDestination,
    ) : PdfLinkTarget

    data class Uri(
        val uri: String,
    ) : PdfLinkTarget {
        init {
            require(uri.isNotBlank()) { "uri must not be blank" }
        }
    }

    data class RemoteDocument(
        val filePath: String?,
        val destination: PdfDestination?,
    ) : PdfLinkTarget

    data class Unsupported(
        val nativeActionType: Int,
    ) : PdfLinkTarget
}

data class PdfLink(
    val bounds: List<PdfQuad>,
    val target: PdfLinkTarget,
)

data class PdfBookmark(
    val title: String,
    val destination: PdfDestination?,
    val target: PdfLinkTarget?,
    val color: PdfColor?,
    val children: List<PdfBookmark>,
)
