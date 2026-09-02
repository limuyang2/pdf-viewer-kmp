package io.github.limuyang2.pdf.core

/**
 * A point in PDF page space. Values are measured in PDF points.
 */
data class PdfPoint(
    /** Horizontal position in PDF points. */
    val x: Double,
    /** Vertical position in PDF points. */
    val y: Double,
) {
    init {
        require(x.isFinite()) { "x must be finite" }
        require(y.isFinite()) { "y must be finite" }
    }
}

/**
 * A size measured in PDF points.
 */
data class PdfSize(
    /** Horizontal extent in PDF points. */
    val width: Double,
    /** Vertical extent in PDF points. */
    val height: Double,
) {
    init {
        require(width.isFinite() && width >= 0.0) {
            "width must be finite and non-negative"
        }
        require(height.isFinite() && height >= 0.0) {
            "height must be finite and non-negative"
        }
    }
}

/**
 * A rectangle in PDF page space, whose origin is at the bottom-left.
 */
data class PdfRect(
    /** Left edge in PDF points. */
    val left: Double,
    /** Bottom edge in PDF points. */
    val bottom: Double,
    /** Right edge in PDF points. */
    val right: Double,
    /** Top edge in PDF points. */
    val top: Double,
) {
    init {
        require(left.isFinite()) { "left must be finite" }
        require(bottom.isFinite()) { "bottom must be finite" }
        require(right.isFinite()) { "right must be finite" }
        require(top.isFinite()) { "top must be finite" }
        require(right >= left) { "right must not be less than left" }
        require(top >= bottom) { "top must not be less than bottom" }
    }

    /** Horizontal extent in PDF points. */
    val width: Double
        get() = right - left

    /** Vertical extent in PDF points. */
    val height: Double
        get() = top - bottom
}

/**
 * A quadrilateral in PDF page space, as reported by PDFium for link areas
 * and search matches. A rectangle is emitted as four corner points.
 */
data class PdfQuad(
    /** First corner point. */
    val first: PdfPoint,
    /** Second corner point. */
    val second: PdfPoint,
    /** Third corner point. */
    val third: PdfPoint,
    /** Fourth corner point. */
    val fourth: PdfPoint,
)

/**
 * A size in device pixels, typically a render target.
 */
data class PdfPixelSize(
    /** Width in pixels. */
    val width: Int,
    /** Height in pixels. */
    val height: Int,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
    }
}

/**
 * Page rotation in 90-degree steps, applied clockwise.
 */
enum class PdfRotation(
    /** Clockwise rotation in degrees. */
    val degrees: Int,
) {
    Degrees0(0),
    Degrees90(90),
    Degrees180(180),
    Degrees270(270),
    ;

    companion object {
        /**
         * Returns the rotation matching [degrees].
         *
         * @throws IllegalArgumentException if [degrees] is not 0, 90, 180, or 270.
         */
        fun fromDegrees(degrees: Int): PdfRotation =
            when (degrees) {
                0 -> Degrees0
                90 -> Degrees90
                180 -> Degrees180
                270 -> Degrees270
                else -> throw IllegalArgumentException(
                    "degrees must be one of 0, 90, 180, or 270",
                )
            }
    }
}

/**
 * Basic geometry of a single page.
 */
data class PdfPageInfo(
    /** Media size in PDF points. */
    val size: PdfSize,
    /** Intrinsic page rotation stored in the document. */
    val rotation: PdfRotation,
    /** Bounding (crop) box, or `null` when the page has none. */
    val boundingBox: PdfRect?,
)
