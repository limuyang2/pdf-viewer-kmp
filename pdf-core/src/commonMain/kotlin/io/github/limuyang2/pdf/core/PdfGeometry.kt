package io.github.limuyang2.pdf.core

/**
 * A point in PDF page space. Values are measured in PDF points.
 */
public data class PdfPoint(
    val x: Double,
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
public data class PdfSize(
    val width: Double,
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
public data class PdfRect(
    val left: Double,
    val bottom: Double,
    val right: Double,
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

    public val width: Double
        get() = right - left

    public val height: Double
        get() = top - bottom
}

public data class PdfQuad(
    val first: PdfPoint,
    val second: PdfPoint,
    val third: PdfPoint,
    val fourth: PdfPoint,
)

public data class PdfPixelSize(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
    }
}

public enum class PdfRotation(
    public val degrees: Int,
) {
    Degrees0(0),
    Degrees90(90),
    Degrees180(180),
    Degrees270(270),
    ;

    public companion object {
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

public data class PdfPageInfo(
    val size: PdfSize,
    val rotation: PdfRotation,
    val boundingBox: PdfRect?,
)
