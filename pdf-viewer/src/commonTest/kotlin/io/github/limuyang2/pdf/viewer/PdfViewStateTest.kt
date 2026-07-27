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
        assertEquals(4f, state.zoom)

        state.updateZoom(0f)
        assertEquals(1f, state.zoom)
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
