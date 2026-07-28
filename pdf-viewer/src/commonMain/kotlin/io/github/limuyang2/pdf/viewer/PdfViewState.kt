package io.github.limuyang2.pdf.viewer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import io.github.limuyang2.pdf.core.PdfDocument
import io.github.limuyang2.pdf.core.PdfSearchMatch
import io.github.limuyang2.pdf.core.PdfSearchOptions
import kotlinx.coroutines.CancellationException

/**
 * Scroll, zoom, search, and render-cache state for [PdfView].
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

    var searchStatus: PdfViewSearchStatus by
        mutableStateOf(PdfViewSearchStatus.Idle)
        private set

    var searchResults: List<PdfViewSearchResult> by
        mutableStateOf(emptyList())
        private set

    var selectedSearchResultIndex: Int by
        mutableIntStateOf(NO_SEARCH_RESULT)
        private set

    val selectedSearchResult: PdfViewSearchResult?
        get() = searchResults.getOrNull(selectedSearchResultIndex)

    val currentPage: Int
        get() = listState.firstVisibleItemIndex

    private var cachedDocument: PdfDocument? = null
    private var searchDocument: PdfDocument? = null
    private var searchGeneration: Long = 0
    private var searchResultsByPage: Map<Int, List<PdfViewSearchResult>> by
        mutableStateOf(emptyMap())
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

    fun clearSearch() {
        searchGeneration++
        searchDocument = null
        resetSearchState()
    }

    fun selectSearchResult(index: Int): PdfViewSearchResult {
        require(index in searchResults.indices) {
            "index must be in searchResults.indices"
        }
        selectedSearchResultIndex = index
        return searchResults[index]
    }

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

    internal fun bind(
        document: PdfDocument,
        maximumZoom: Float,
    ) {
        updateMaximumZoom(maximumZoom)
        if (cachedDocument !== document) {
            cachedDocument = document
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
    val pageIndex: Int,
    val match: PdfSearchMatch,
) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
    }
}

/**
 * Current document-search lifecycle.
 */
sealed interface PdfViewSearchStatus {
    data object Idle : PdfViewSearchStatus

    data class Searching(
        val query: String,
        val completedPageCount: Int,
        val totalPageCount: Int,
    ) : PdfViewSearchStatus

    data class Completed(
        val query: String,
        val resultCount: Int,
    ) : PdfViewSearchStatus

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
