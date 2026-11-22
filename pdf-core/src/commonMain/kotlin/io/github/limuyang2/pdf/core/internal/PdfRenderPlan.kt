package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPoint
import io.github.limuyang2.pdf.core.PdfRect
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfTileRenderRequest
import kotlin.math.max
import kotlin.math.min

/** Matrix maps PDFium's intrinsic-rotation-normalized display space to the bitmap. */
internal data class PdfRenderPlan(
    val startX: Int,
    val startY: Int,
    val fullWidth: Int,
    val fullHeight: Int,
    val matrix: FloatArray? = null,
    val clip: FloatArray? = null,
)

internal fun renderPlan(
    request: PdfRenderRequest,
    tile: PdfTileRenderRequest?,
    information: () -> PdfPageInfo,
): PdfRenderPlan {
    if (tile != null) {
        return PdfRenderPlan(
            -tile.tileBounds.left, -tile.tileBounds.top,
            tile.fullPageSize.width, tile.fullPageSize.height,
        )
    }
    val width = request.outputSize.width
    val height = request.outputSize.height
    val source = request.sourceRect ?: return PdfRenderPlan(0, 0, width, height)
    val info = information()
    val box = requireNotNull(info.boundingBox) { "page has no valid visible bounding box" }
    require(info.size.width > 0.0 && info.size.height > 0.0) { "page dimensions must be positive" }

    val swapsDimensions = info.rotation.degrees == 90 || info.rotation.degrees == 270
    val displayWidth = if (swapsDimensions) info.size.height else info.size.width
    val displayHeight = if (swapsDimensions) info.size.width else info.size.height

    // Raw PDF user space -> PDFium's unscaled top-left display space.
    fun display(x: Double, y: Double): PdfPoint = when (info.rotation.degrees) {
        0 -> PdfPoint(x - box.left, box.top - y)
        90 -> PdfPoint(y - box.bottom, x - box.left)
        180 -> PdfPoint(box.right - x, y - box.bottom)
        else -> PdfPoint(box.top - y, box.right - x)
    }
    // The native matrix API already applies the intrinsic page transform.
    fun rotate(p: PdfPoint): PdfPoint = when (request.rotation.degrees) {
        0 -> p
        90 -> PdfPoint(displayHeight - p.y, p.x)
        180 -> PdfPoint(displayWidth - p.x, displayHeight - p.y)
        else -> PdfPoint(p.y, displayWidth - p.x)
    }
    fun bounds(points: List<PdfPoint>) = PdfRect(
        points.minOf { it.x }, points.minOf { it.y },
        points.maxOf { it.x }, points.maxOf { it.y },
    )
    val region = bounds(listOf(
        display(source.left, source.bottom), display(source.left, source.top),
        display(source.right, source.bottom), display(source.right, source.top),
    ).map(::rotate))
    val scaleX = width / region.width
    val scaleY = height / region.height
    fun output(p: PdfPoint): PdfPoint {
        val rotated = rotate(p)
        return PdfPoint((rotated.x - region.left) * scaleX, (rotated.y - region.bottom) * scaleY)
    }
    val origin = output(PdfPoint(0.0, 0.0))
    // Use rotation basis directly to avoid cancellation with large translations.
    val basis = when (request.rotation.degrees) {
        0 -> doubleArrayOf(scaleX, 0.0, 0.0, scaleY)
        90 -> doubleArrayOf(0.0, scaleY, -scaleX, 0.0)
        180 -> doubleArrayOf(-scaleX, 0.0, 0.0, -scaleY)
        else -> doubleArrayOf(0.0, -scaleY, scaleX, 0.0)
    }
    val matrix = (basis.toList() + listOf(origin.x, origin.y)).map { it.toFloat() }.toFloatArray()
    require(matrix.all { it.isFinite() }) { "region transform exceeds native float range" }
    val determinant = matrix[0].toDouble() * matrix[3] - matrix[1].toDouble() * matrix[2]
    require(determinant.isFinite() && determinant != 0.0) { "region transform must be invertible" }

    // Explicitly exclude content outside the visible page even for oversized regions.
    val visible = bounds(listOf(
        output(PdfPoint(0.0, 0.0)), output(PdfPoint(displayWidth, 0.0)),
        output(PdfPoint(0.0, displayHeight)), output(PdfPoint(displayWidth, displayHeight)),
    ))
    val left = max(0.0, min(width.toDouble(), visible.left))
    val top = max(0.0, min(height.toDouble(), visible.bottom))
    val right = max(left, min(width.toDouble(), visible.right))
    val bottom = max(top, min(height.toDouble(), visible.top))
    return PdfRenderPlan(0, 0, width, height, matrix, floatArrayOf(
        left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(),
    ))
}
