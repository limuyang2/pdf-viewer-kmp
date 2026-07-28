package io.github.limuyang2.pdf.viewer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import io.github.limuyang2.pdf.core.PdfSearchMatch
import io.github.limuyang2.pdf.core.PdfTextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class PdfViewStateTest {
    @Test
    fun zoomIsClampedToSupportedRange() {
        val state = createState()

        state.updateZoom(10f)
        assertEquals(PdfViewState.DEFAULT_MAX_ZOOM, state.zoom)

        state.updateZoom(0f)
        assertEquals(PdfViewState.MIN_ZOOM, state.zoom)
    }

    @Test
    fun zoomUsesConfiguredMaximum() {
        val state = createState()

        state.updateMaximumZoom(6f)
        state.updateZoom(5f)
        assertEquals(5f, state.zoom)

        state.updateMaximumZoom(2f)
        assertEquals(2f, state.zoom)
        state.zoomBy(2f)
        assertEquals(2f, state.zoom)
    }

    @Test
    fun maximumZoomMustBeFiniteAndAtLeastMinimum() {
        val state = createState()

        assertFailsWith<IllegalArgumentException> {
            state.updateMaximumZoom(0.5f)
        }
        assertFailsWith<IllegalArgumentException> {
            state.updateMaximumZoom(Float.NaN)
        }
    }

    @Test
    fun zoomFactorMustBePositiveAndFinite() {
        val state = createState()

        assertFailsWith<IllegalArgumentException> {
            state.zoomBy(0f)
        }
        assertFailsWith<IllegalArgumentException> {
            state.zoomBy(Float.NaN)
        }
    }

    @Test
    fun searchResultSelectionSupportsNavigationAndWrapping() {
        val state = createState()
        val results =
            listOf(
                searchResult(pageIndex = 0, startCharacterIndex = 2),
                searchResult(pageIndex = 1, startCharacterIndex = 4),
                searchResult(pageIndex = 1, startCharacterIndex = 8),
            )

        state.updateSearchResults(results)

        assertEquals(0, state.selectedSearchResultIndex)
        assertSame(results[0], state.selectedSearchResult)
        assertSame(results[1], state.selectNextSearchResult())
        assertSame(results[0], state.selectPreviousSearchResult())
        assertSame(results[2], state.selectPreviousSearchResult())
        assertSame(results[0], state.selectNextSearchResult())
    }

    @Test
    fun searchResultNavigationCanStopAtEitherEnd() {
        val state = createState()
        val results =
            listOf(
                searchResult(pageIndex = 0, startCharacterIndex = 2),
                searchResult(pageIndex = 1, startCharacterIndex = 4),
            )

        state.updateSearchResults(results)

        assertSame(results[0], state.selectPreviousSearchResult(false))
        assertSame(results[1], state.selectSearchResult(1))
        assertSame(results[1], state.selectNextSearchResult(false))
    }

    @Test
    fun clearingResultsClearsSelection() {
        val state = createState()
        state.updateSearchResults(
            listOf(searchResult(pageIndex = 0, startCharacterIndex = 2)),
        )

        state.updateSearchResults(emptyList())

        assertEquals(-1, state.selectedSearchResultIndex)
        assertNull(state.selectedSearchResult)
        assertNull(state.selectNextSearchResult())
        assertNull(state.selectPreviousSearchResult())
    }

    @Test
    fun selectingSearchResultRequiresValidIndex() {
        val state = createState()

        assertFailsWith<IllegalArgumentException> {
            state.selectSearchResult(0)
        }
    }

    private fun createState(): PdfViewState =
        PdfViewState(
            listState = LazyListState(),
            horizontalScrollState = ScrollState(0),
            initialZoom = 1f,
        )

    private fun searchResult(
        pageIndex: Int,
        startCharacterIndex: Int,
    ): PdfViewSearchResult =
        PdfViewSearchResult(
            pageIndex = pageIndex,
            match =
                PdfSearchMatch(
                    range =
                        PdfTextRange(
                            startCharacterIndex = startCharacterIndex,
                            characterCount = 2,
                        ),
                    bounds = emptyList(),
                ),
        )
}
