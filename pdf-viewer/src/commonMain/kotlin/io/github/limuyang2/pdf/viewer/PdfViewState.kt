package io.github.limuyang2.pdf.viewer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import io.github.limuyang2.pdf.core.PdfDocument
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfSearchMatch
import io.github.limuyang2.pdf.core.PdfSearchOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Scroll, zoom, search, and render-cache state for [PdfView].
 */
@Stable
class PdfViewState internal constructor(
    /** Vertical lazy-list state backing the page list. */
    val listState: LazyListState,
    /** Horizontal scroll state used when a zoomed page is wider than the viewport. */
    val horizontalScrollState: ScrollState,
    initialZoom: Float,
    initialMaximumZoom: Float = DEFAULT_MAX_ZOOM,
) {
    private var maximumZoom: Float =
        validateMaximumZoom(initialMaximumZoom)

    /** Current visual zoom multiplier, clamped to the configured range. */
    var zoom: Float by mutableFloatStateOf(validateZoom(initialZoom))
        private set

    /** Lifecycle state of the active document search. */
    var searchStatus: PdfViewSearchStatus by
        mutableStateOf(PdfViewSearchStatus.Idle)
        private set

    /** Search results ordered by page and match position. */
    var searchResults: List<PdfViewSearchResult> by
        mutableStateOf(emptyList())
        private set

    /** Index of the selected result, or `-1` when there is no result. */
    var selectedSearchResultIndex: Int by
        mutableIntStateOf(NO_SEARCH_RESULT)
        private set

    /** Currently selected result, or `null` when the result list is empty. */
    val selectedSearchResult: PdfViewSearchResult?
        get() = searchResults.getOrNull(selectedSearchResultIndex)

    /** Zero-based index of the first visible page. */
    val currentPage: Int
        get() = listState.firstVisibleItemIndex

    private var cachedDocument: PdfDocument? = null
    private var searchDocument: PdfDocument? = null
    private var searchGeneration: Long = 0
    private var searchScrollGeneration: Long = 0
    private var layoutMetrics: PdfViewLayoutMetrics? = null
    private var searchResultsByPage: Map<Int, List<PdfViewSearchResult>> by
        mutableStateOf(emptyMap())
    private val pageInformationCache =
        mutableStateMapOf<Int, PdfPageInfo>()
    private val renderCache =
        PdfRenderCache<ImageBitmap>(MAX_CACHED_BITMAP_BYTES)

    /** Sets an absolute zoom value within the supported range. */
    fun updateZoom(zoom: Float) {
        this.zoom = validateZoom(zoom)
    }

    /** Multiplies the current zoom by a positive [factor]. */
    fun zoomBy(factor: Float) {
        require(factor.isFinite() && factor > 0f) {
            "factor must be finite and positive"
        }
        updateZoom(zoom * factor)
    }

    /** Immediately places [pageIndex] at the requested list offset. */
    suspend fun scrollToPage(
        pageIndex: Int,
        scrollOffset: Int = 0,
    ) {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
        require(scrollOffset >= 0) { "scrollOffset must be non-negative" }
        listState.scrollToItem(pageIndex, scrollOffset)
    }

    /** Smoothly places [pageIndex] at the requested list offset. */
    suspend fun animateScrollToPage(
        pageIndex: Int,
        scrollOffset: Int = 0,
    ) {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
        require(scrollOffset >= 0) { "scrollOffset must be non-negative" }
        listState.animateScrollToItem(pageIndex, scrollOffset)
    }

    /** Releases all cached page images without changing scroll or zoom. */
    fun clearRenderCache() {
        renderCache.clear()
    }

    /**
     * Searches every page in [document].
     *
     * Calling this function replaces the previous search. An empty [query]
     * clears the search. If an older search finishes after a newer one starts,
     * its results are ignored.
     */
    suspend fun search(
        document: PdfDocument,
        query: String,
        options: PdfSearchOptions = PdfSearchOptions(),
    ) {
        if (query.isEmpty()) {
            clearSearch()
            return
        }
        require('\u0000' !in query) {
            "query must not contain a null character"
        }

        val generation = beginSearch(document, query)
        val results = mutableListOf<PdfViewSearchResult>()
        val resultsByPage =
            mutableMapOf<Int, List<PdfViewSearchResult>>()
        try {
            repeat(document.pageCount) { pageIndex ->
                val pageResults =
                    document[pageIndex]
                        .search(query, options)
                        .map { match ->
                            PdfViewSearchResult(
                                pageIndex = pageIndex,
                                match = match,
                            )
                        }
                if (!isCurrentSearch(generation, document)) {
                    return
                }
                results += pageResults
                if (pageResults.isNotEmpty()) {
                    resultsByPage[pageIndex] = pageResults
                }
                updateSearchResults(
                    results = results.toList(),
                    resultsByPage = resultsByPage.toMap(),
                )
                searchStatus =
                    PdfViewSearchStatus.Searching(
                        query = query,
                        completedPageCount = pageIndex + 1,
                        totalPageCount = document.pageCount,
                    )
            }
            if (isCurrentSearch(generation, document)) {
                searchStatus =
                    PdfViewSearchStatus.Completed(
                        query = query,
                        resultCount = results.size,
                    )
            }
        } catch (cancellation: CancellationException) {
            if (isCurrentSearch(generation, document)) {
                resetSearchState()
            }
            throw cancellation
        } catch (failure: Throwable) {
            if (isCurrentSearch(generation, document)) {
                updateSearchResults(emptyList())
                searchStatus =
                    PdfViewSearchStatus.Failed(
                        query = query,
                        error = failure,
                    )
            }
        }
    }

    /** Cancels the active search state and removes every highlight. */
    fun clearSearch() {
        searchGeneration++
        searchScrollGeneration++
        searchDocument = null
        resetSearchState()
    }

    /** Selects and returns the result at [index] without scrolling. */
    fun selectSearchResult(index: Int): PdfViewSearchResult {
        require(index in searchResults.indices) {
            "index must be in searchResults.indices"
        }
        selectedSearchResultIndex = index
        return searchResults[index]
    }

    /** Selects the next result, optionally wrapping to the first result. */
    fun selectNextSearchResult(
        wrapAround: Boolean = true,
    ): PdfViewSearchResult? {
        if (searchResults.isEmpty()) return null
        val nextIndex = selectedSearchResultIndex + 1
        val targetIndex =
            when {
                nextIndex in searchResults.indices -> nextIndex
                wrapAround -> 0
                else -> searchResults.lastIndex
            }
        return selectSearchResult(targetIndex)
    }

    /** Selects the previous result, optionally wrapping to the last result. */
    fun selectPreviousSearchResult(
        wrapAround: Boolean = true,
    ): PdfViewSearchResult? {
        if (searchResults.isEmpty()) return null
        val previousIndex = selectedSearchResultIndex - 1
        val targetIndex =
            when {
                previousIndex in searchResults.indices -> previousIndex
                wrapAround -> searchResults.lastIndex
                else -> 0
            }
        return selectSearchResult(targetIndex)
    }

    /**
     * Immediately scrolls [result] to its position inside the PDF page.
     *
     * A [PdfView] using this state must already have completed a layout pass.
     */
    suspend fun scrollToSearchResult(
        result: PdfViewSearchResult,
        alignment: PdfSearchScrollAlignment =
            PdfSearchScrollAlignment.Default,
    ) {
        scrollToSearchResult(
            result = result,
            alignment = alignment,
            animated = false,
        )
    }

    /**
     * Smoothly scrolls [result] to its position inside the PDF page.
     *
     * A [PdfView] using this state must already have completed a layout pass.
     */
    suspend fun animateScrollToSearchResult(
        result: PdfViewSearchResult,
        alignment: PdfSearchScrollAlignment =
            PdfSearchScrollAlignment.Default,
    ) {
        scrollToSearchResult(
            result = result,
            alignment = alignment,
            animated = true,
        )
    }

    internal fun bind(
        document: PdfDocument,
        maximumZoom: Float,
    ) {
        updateMaximumZoom(maximumZoom)
        if (cachedDocument !== document) {
            cachedDocument = document
            searchScrollGeneration++
            layoutMetrics = null
            pageInformationCache.clear()
            clearRenderCache()
            if (searchDocument !== document) {
                clearSearch()
            }
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

    internal fun updateLayoutMetrics(
        document: PdfDocument,
        metrics: PdfViewLayoutMetrics,
    ) {
        if (cachedDocument === document && layoutMetrics != metrics) {
            layoutMetrics = metrics
        }
    }

    internal fun cachedPageInformation(
        document: PdfDocument,
        pageIndex: Int,
    ): PdfPageInfo? =
        if (cachedDocument === document) {
            pageInformationCache[pageIndex]
        } else {
            null
        }

    internal fun cachePageInformation(
        document: PdfDocument,
        pageIndex: Int,
        information: PdfPageInfo,
    ) {
        if (cachedDocument === document) {
            pageInformationCache[pageIndex] = information
        }
    }

    internal fun searchResultsFor(
        document: PdfDocument,
        pageIndex: Int,
    ): List<PdfViewSearchResult> =
        if (searchDocument === document) {
            searchResultsByPage[pageIndex].orEmpty()
        } else {
            emptyList()
        }

    internal fun updateSearchResults(
        results: List<PdfViewSearchResult>,
        resultsByPage: Map<Int, List<PdfViewSearchResult>> =
            results.groupBy(PdfViewSearchResult::pageIndex),
    ) {
        searchResults = results
        searchResultsByPage = resultsByPage
        selectedSearchResultIndex =
            when {
                results.isEmpty() -> NO_SEARCH_RESULT
                selectedSearchResultIndex in results.indices ->
                    selectedSearchResultIndex
                else -> 0
            }
    }

    private fun beginSearch(
        document: PdfDocument,
        query: String,
    ): Long {
        val generation = ++searchGeneration
        searchScrollGeneration++
        searchDocument = document
        updateSearchResults(emptyList())
        searchStatus =
            PdfViewSearchStatus.Searching(
                query = query,
                completedPageCount = 0,
                totalPageCount = document.pageCount,
            )
        return generation
    }

    private fun isCurrentSearch(
        generation: Long,
        document: PdfDocument,
    ): Boolean =
        searchGeneration == generation &&
            searchDocument === document

    private fun resetSearchState() {
        updateSearchResults(emptyList())
        searchStatus = PdfViewSearchStatus.Idle
    }

    private suspend fun scrollToSearchResult(
        result: PdfViewSearchResult,
        alignment: PdfSearchScrollAlignment,
        animated: Boolean,
    ) {
        require(result in searchResults) {
            "result must belong to the current searchResults"
        }
        val document =
            checkNotNull(cachedDocument) {
                "A PdfView must be bound before scrolling to a search result"
            }
        check(searchDocument === document) {
            "The current search results do not belong to the bound PdfView"
        }
        val requestGeneration = ++searchScrollGeneration
        // Loading page information here allows off-screen targets to use
        // their final aspect ratio before LazyColumn measures the page.
        val information =
            cachedPageInformation(document, result.pageIndex)
                ?: document[result.pageIndex]
                    .information()
                    .also {
                        cachePageInformation(
                            document = document,
                            pageIndex = result.pageIndex,
                            information = it,
                        )
                    }
        if (!isCurrentSearchScroll(requestGeneration, document)) {
            return
        }
        val metrics =
            checkNotNull(layoutMetrics) {
                "PdfView must complete a layout pass before scrolling to a search result"
            }
        val target =
            calculateSearchScrollTarget(
                result = result,
                pageInformation = information,
                metrics = metrics,
                alignment = alignment,
            )
        if (target == null) {
            if (animated) {
                listState.animateScrollToItem(result.pageIndex)
            } else {
                listState.scrollToItem(result.pageIndex)
            }
            return
        }

        coroutineScope {
            // Horizontal positioning matters when zoom makes a page wider
            // than the viewport.
            launch {
                val horizontalTarget =
                    target.horizontalScrollOffset
                        .coerceIn(0, horizontalScrollState.maxValue)
                if (animated) {
                    horizontalScrollState.animateScrollTo(horizontalTarget)
                } else {
                    horizontalScrollState.scrollTo(horizontalTarget)
                }
            }

            val isVisible =
                listState.layoutInfo.visibleItemsInfo.any {
                    it.index == result.pageIndex
                }
            if (!isVisible) {
                if (animated) {
                    listState.animateScrollToItem(
                        index = result.pageIndex,
                        scrollOffset = target.verticalScrollOffset,
                    )
                } else {
                    listState.scrollToItem(
                        index = result.pageIndex,
                        scrollOffset = target.verticalScrollOffset,
                    )
                }
            }
            if (isCurrentSearchScroll(requestGeneration, document)) {
                // The first scroll uses an estimated item position. Correct
                // it with LazyColumn's measured item offset once visible.
                correctSearchResultVerticalPosition(
                    pageIndex = result.pageIndex,
                    matchCenterY = target.matchCenterY,
                    alignment = alignment,
                    animated = animated,
                )
            }
        }
    }

    private suspend fun correctSearchResultVerticalPosition(
        pageIndex: Int,
        matchCenterY: Float,
        alignment: PdfSearchScrollAlignment,
        animated: Boolean,
    ) {
        val layoutInfo = listState.layoutInfo
        val item =
            layoutInfo.visibleItemsInfo.firstOrNull {
                it.index == pageIndex
            } ?: return
        val viewportSize =
            layoutInfo.viewportEndOffset -
                layoutInfo.viewportStartOffset
        val desiredY =
            layoutInfo.viewportStartOffset +
                viewportSize * alignment.verticalFraction
        val currentY = item.offset + matchCenterY
        val correction = currentY - desiredY
        if (abs(correction) < MIN_SCROLL_CORRECTION_PIXELS) {
            return
        }
        if (animated) {
            listState.animateScrollBy(correction)
        } else {
            listState.scrollBy(correction)
        }
    }

    private fun isCurrentSearchScroll(
        generation: Long,
        document: PdfDocument,
    ): Boolean =
        searchScrollGeneration == generation &&
            cachedDocument === document &&
            searchDocument === document

    private fun validateZoom(zoom: Float): Float {
        require(zoom.isFinite()) { "zoom must be finite" }
        return zoom.coerceIn(MIN_ZOOM, maximumZoom)
    }

    internal companion object {
        const val MIN_ZOOM: Float = 1f
        const val DEFAULT_MAX_ZOOM: Float = 4f
        private const val BYTES_PER_PIXEL: Long = 4
        private const val MAX_CACHED_BITMAP_BYTES: Long = 64L * 1024 * 1024
        private const val NO_SEARCH_RESULT: Int = -1
        private const val MIN_SCROLL_CORRECTION_PIXELS: Float = 0.5f

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
 * A page-scoped search match exposed by [PdfViewState.searchResults].
 */
data class PdfViewSearchResult(
    /** Zero-based index of the page containing this match. */
    val pageIndex: Int,
    /** Text range and page-space rectangles returned by PDFium. */
    val match: PdfSearchMatch,
) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
    }
}

/**
 * Position of a search result within the PDF viewport.
 *
 * Fractions are measured from the viewport's top-left corner.
 */
data class PdfSearchScrollAlignment(
    /** Desired vertical viewport position, from `0f` (top) to `1f` (bottom). */
    val verticalFraction: Float = DEFAULT_VERTICAL_FRACTION,
    /** Desired horizontal viewport position, from `0f` (left) to `1f` (right). */
    val horizontalFraction: Float = DEFAULT_HORIZONTAL_FRACTION,
) {
    init {
        require(verticalFraction.isFinite() && verticalFraction in 0f..1f) {
            "verticalFraction must be finite and in 0f..1f"
        }
        require(
            horizontalFraction.isFinite() &&
                horizontalFraction in 0f..1f,
        ) {
            "horizontalFraction must be finite and in 0f..1f"
        }
    }

    companion object {
        /** Places a result slightly above center to retain reading context. */
        val Default: PdfSearchScrollAlignment =
            PdfSearchScrollAlignment()

        /** Places a result at the exact center of the viewport. */
        val Center: PdfSearchScrollAlignment =
            PdfSearchScrollAlignment(
                verticalFraction = 0.5f,
                horizontalFraction = 0.5f,
            )

        private const val DEFAULT_VERTICAL_FRACTION: Float = 0.4f
        private const val DEFAULT_HORIZONTAL_FRACTION: Float = 0.5f
    }
}

/**
 * Current document-search lifecycle.
 */
sealed interface PdfViewSearchStatus {
    /** No search has been started, or the previous one was cleared. */
    data object Idle : PdfViewSearchStatus

    /**
     * A search is running and results arrive page by page.
     *
     * @property query the searched text.
     * @property completedPageCount how many pages have been searched.
     * @property totalPageCount total number of pages to search.
     */
    data class Searching(
        val query: String,
        val completedPageCount: Int,
        val totalPageCount: Int,
    ) : PdfViewSearchStatus

    /**
     * The search finished successfully.
     *
     * @property query the searched text.
     * @property resultCount number of matches across the document.
     */
    data class Completed(
        val query: String,
        val resultCount: Int,
    ) : PdfViewSearchStatus

    /**
     * The search aborted with an error; collected results are cleared.
     *
     * @property query the searched text.
     * @property error the failure that stopped the search.
     */
    data class Failed(
        val query: String,
        val error: Throwable,
    ) : PdfViewSearchStatus
}

/**
 * Remembers scroll position, zoom, search state, and the small page-render
 * cache. Search results are document-bound and are not saved.
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

internal data class PdfViewLayoutMetrics(
    val viewportWidth: Int,
    val viewportHeight: Int,
    val displayedPageWidth: Int,
    val pagePadding: Int,
) {
    init {
        require(viewportWidth > 0) { "viewportWidth must be positive" }
        require(viewportHeight > 0) { "viewportHeight must be positive" }
        require(displayedPageWidth > 0) {
            "displayedPageWidth must be positive"
        }
        require(pagePadding >= 0) { "pagePadding must be non-negative" }
    }
}

internal data class PdfSearchScrollTarget(
    val verticalScrollOffset: Int,
    val horizontalScrollOffset: Int,
    val matchCenterY: Float,
)

internal fun calculateSearchScrollTarget(
    result: PdfViewSearchResult,
    pageInformation: PdfPageInfo,
    metrics: PdfViewLayoutMetrics,
    alignment: PdfSearchScrollAlignment,
): PdfSearchScrollTarget? {
    if (result.match.bounds.isEmpty()) {
        return null
    }
    val displayedPageHeight =
        metrics.displayedPageWidth *
            (pageInformation.size.height / pageInformation.size.width)
    val displayedBounds =
        result.match.bounds.map { bounds ->
            pdfRectToDisplayedRect(
                bounds = bounds,
                displayedWidth = metrics.displayedPageWidth.toFloat(),
                displayedHeight = displayedPageHeight.toFloat(),
                pageInformation = pageInformation,
            )
        }
    val left = displayedBounds.minOf { it.left }
    val top = displayedBounds.minOf { it.top }
    val right = displayedBounds.maxOf { it.right }
    val bottom = displayedBounds.maxOf { it.bottom }
    val matchCenterX = (left + right) / 2f
    val matchCenterY = (top + bottom) / 2f
    return PdfSearchScrollTarget(
        verticalScrollOffset =
            (
                matchCenterY -
                    metrics.viewportHeight *
                    alignment.verticalFraction
            ).roundToInt(),
        horizontalScrollOffset =
            (
                metrics.pagePadding +
                    matchCenterX -
                    metrics.viewportWidth *
                    alignment.horizontalFraction
            ).roundToInt(),
        matchCenterY = matchCenterY,
    )
}
