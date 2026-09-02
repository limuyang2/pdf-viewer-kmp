package io.github.limuyang2.pdf.core

/**
 * A location inside a document that a link or bookmark points to.
 */
data class PdfDestination(
    /** Zero-based index of the destination page. */
    val pageIndex: Int,
    /** How the destination page should be positioned and scaled. */
    val view: PdfDestinationView,
) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
    }
}

/**
 * The view mode a [PdfDestination] requests for its page.
 */
sealed interface PdfDestinationView {
    /**
     * A view mode PDFium reported that has no typed counterpart.
     *
     * @property nativeViewMode raw PDFium view mode identifier.
     * @property parameters up to four raw view parameters in PDF points.
     */
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

    /**
     * A specific position and zoom level.
     *
     * @property x target horizontal position in PDF points, or `null` to
     * keep the current position.
     * @property y target vertical position in PDF points, or `null` to keep
     * the current position.
     * @property zoom target zoom factor, or `null` to keep the current zoom.
     */
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

    /** Fits the whole page inside the window. */
    data object FitPage : PdfDestinationView

    /**
     * Fits the page width inside the window.
     *
     * @property top vertical position in PDF points to keep visible, or
     * `null` for the default.
     */
    data class FitHorizontally(
        val top: Double?,
    ) : PdfDestinationView {
        init {
            require(top == null || top.isFinite()) {
                "top must be null or finite"
            }
        }
    }

    /**
     * Fits the page height inside the window.
     *
     * @property left horizontal position in PDF points to keep visible, or
     * `null` for the default.
     */
    data class FitVertically(
        val left: Double?,
    ) : PdfDestinationView {
        init {
            require(left == null || left.isFinite()) {
                "left must be null or finite"
            }
        }
    }

    /**
     * Shows an arbitrary rectangle of the page.
     *
     * @property bounds rectangle in PDF points to display.
     */
    data class FitRectangle(
        val bounds: PdfRect,
    ) : PdfDestinationView

    /** Fits the page's bounding box inside the window. */
    data object FitBoundingBox : PdfDestinationView

    /**
     * Fits the bounding-box width inside the window.
     *
     * @property top vertical position in PDF points to keep visible, or
     * `null` for the default.
     */
    data class FitBoundingBoxHorizontally(
        val top: Double?,
    ) : PdfDestinationView {
        init {
            require(top == null || top.isFinite()) {
                "top must be null or finite"
            }
        }
    }

    /**
     * Fits the bounding-box height inside the window.
     *
     * @property left horizontal position in PDF points to keep visible, or
     * `null` for the default.
     */
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

/**
 * What a link annotation activates when clicked.
 */
sealed interface PdfLinkTarget {
    /** A destination inside the same document. */
    data class Internal(
        val destination: PdfDestination,
    ) : PdfLinkTarget

    /**
     * An external URI, typically `http:` or `https:`.
     *
     * @property uri the URI to open.
     */
    data class Uri(
        val uri: String,
    ) : PdfLinkTarget {
        init {
            require(uri.isNotBlank()) { "uri must not be blank" }
        }
    }

    /**
     * A destination in another PDF file.
     *
     * @property filePath path of the referenced file, or `null` when PDFium
     * did not report one.
     * @property destination location inside the referenced file, or `null`.
     */
    data class RemoteDocument(
        val filePath: String?,
        val destination: PdfDestination?,
    ) : PdfLinkTarget

    /**
     * An action the library does not model.
     *
     * @property nativeActionType raw PDFium action identifier.
     */
    data class Unsupported(
        val nativeActionType: Int,
    ) : PdfLinkTarget
}

/**
 * A clickable link annotation on a page.
 */
data class PdfLink(
    /** Quadrilaterals covering the link area in PDF page coordinates. */
    val bounds: List<PdfQuad>,
    /** What the link activates. */
    val target: PdfLinkTarget,
)

/**
 * An entry of the document outline (bookmark tree).
 */
data class PdfBookmark(
    /** Display title of the entry. */
    val title: String,
    /** Location this entry jumps to, when it points inside the document. */
    val destination: PdfDestination?,
    /** Raw link target, set when the entry points elsewhere. */
    val target: PdfLinkTarget?,
    /** Custom display color, or `null` for the viewer default. */
    val color: PdfColor?,
    /** Nested child entries in document order. */
    val children: List<PdfBookmark>,
)
