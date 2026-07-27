package io.github.limuyang2.pdf.viewer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    private fun createState(): PdfViewState =
        PdfViewState(
            listState = LazyListState(),
            horizontalScrollState = ScrollState(0),
            initialZoom = 1f,
        )
}
