package io.github.limuyang2.pdf.viewer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import io.github.limuyang2.pdf.core.PdfDocument

/**
 * Scroll, zoom, and render-cache state for [PdfView].
 */
@Stable
class PdfViewState internal constructor(
    val listState: LazyListState,
    val horizontalScrollState: ScrollState,
    initialZoom: Float,
    initialMaximumZoom: Float = DEFAULT_MAX_ZOOM,
) {
    private var maximumZoom: Float =
        validateMaximumZoom(initialMaximumZoom)

    var zoom: Float by mutableFloatStateOf(validateZoom(initialZoom))
        private set

    val currentPage: Int
        get() = listState.firstVisibleItemIndex

    private var cachedDocument: PdfDocument? = null
    private val renderCache =
        PdfRenderCache<ImageBitmap>(MAX_CACHED_BITMAP_BYTES)

    fun updateZoom(zoom: Float) {
        this.zoom = validateZoom(zoom)
    }

    fun zoomBy(factor: Float) {
        require(factor.isFinite() && factor > 0f) {
            "factor must be finite and positive"
        }
        updateZoom(zoom * factor)
    }

    suspend fun scrollToPage(
        pageIndex: Int,
        scrollOffset: Int = 0,
    ) {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
        require(scrollOffset >= 0) { "scrollOffset must be non-negative" }
        listState.scrollToItem(pageIndex, scrollOffset)
    }

    suspend fun animateScrollToPage(
        pageIndex: Int,
        scrollOffset: Int = 0,
    ) {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
        require(scrollOffset >= 0) { "scrollOffset must be non-negative" }
        listState.animateScrollToItem(pageIndex, scrollOffset)
    }

    fun clearRenderCache() {
        renderCache.clear()
    }

    internal fun bind(
        document: PdfDocument,
        maximumZoom: Float,
    ) {
        updateMaximumZoom(maximumZoom)
        if (cachedDocument !== document) {
            cachedDocument = document
            clearRenderCache()
        }
    }

    internal fun updateMaximumZoom(maximumZoom: Float) {
        val validatedMaximumZoom = validateMaximumZoom(maximumZoom)
        if (this.maximumZoom == validatedMaximumZoom) {
            return
        }
        this.maximumZoom = validatedMaximumZoom
        zoom = zoom.coerceAtMost(validatedMaximumZoom)
    }

    internal fun cachedImage(
        document: PdfDocument,
        key: PdfRenderCacheKey,
    ): ImageBitmap? {
        if (cachedDocument !== document) return null
        return renderCache.get(key)
    }

    internal fun cacheImage(
        document: PdfDocument,
        key: PdfRenderCacheKey,
        image: ImageBitmap,
    ) {
        if (cachedDocument !== document) return
        renderCache.put(
            key = key,
            value = image,
            byteCount = key.width.toLong() * key.height * BYTES_PER_PIXEL,
        )
    }

    private fun validateZoom(zoom: Float): Float {
        require(zoom.isFinite()) { "zoom must be finite" }
        return zoom.coerceIn(MIN_ZOOM, maximumZoom)
    }

    internal companion object {
        const val MIN_ZOOM: Float = 1f
        const val DEFAULT_MAX_ZOOM: Float = 4f
        private const val BYTES_PER_PIXEL: Long = 4
        private const val MAX_CACHED_BITMAP_BYTES: Long = 64L * 1024 * 1024

        val Saver: Saver<PdfViewState, List<Number>> =
            Saver(
                save = {
                    listOf(
                        it.listState.firstVisibleItemIndex,
                        it.listState.firstVisibleItemScrollOffset,
                        it.horizontalScrollState.value,
                        it.zoom,
                        it.maximumZoom,
                    )
                },
                restore = {
                    PdfViewState(
                        listState =
                            LazyListState(
                                firstVisibleItemIndex = it[0].toInt(),
                                firstVisibleItemScrollOffset = it[1].toInt(),
                            ),
                        horizontalScrollState =
                            ScrollState(initial = it[2].toInt()),
                        initialZoom = it[3].toFloat(),
                        initialMaximumZoom = it[4].toFloat(),
                    )
                },
            )

        private fun validateMaximumZoom(maximumZoom: Float): Float {
            require(maximumZoom.isFinite() && maximumZoom >= MIN_ZOOM) {
                "maximumZoom must be finite and at least $MIN_ZOOM"
            }
            return maximumZoom
        }
    }
}

/**
 * Remembers scroll position, zoom, and the small page-render cache.
 */
@Composable
fun rememberPdfViewState(
    initialPage: Int = 0,
    initialZoom: Float = 1f,
): PdfViewState {
    require(initialPage >= 0) { "initialPage must be non-negative" }
    return rememberSaveable(saver = PdfViewState.Saver) {
        PdfViewState(
            listState = LazyListState(initialPage),
            horizontalScrollState = ScrollState(0),
            initialZoom = initialZoom,
        )
    }
}

internal data class PdfRenderCacheKey(
    val pageIndex: Int,
    val width: Int,
    val height: Int,
)
