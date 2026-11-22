package io.github.limuyang2.pdfdemo

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.renderComposeScene
import io.github.limuyang2.pdf.viewer.PdfViewSearchStatus
import kotlin.test.Test

class SearchTextFieldLayoutTest {
    @Test
    fun searchControlsMeasureWithInitialQuery() {
        renderControls("PDF", 400)
    }

    @Test
    fun searchControlsMeasureWithEmptyQueryAndNarrowViewport() {
        renderControls("", 320)
    }

    @Test
    fun searchControlsMeasureWithLongQueryAndWideViewport() {
        renderControls("A longer search query with 中文", 800)
    }

    private fun renderControls(query: String, width: Int) {
        // Exercise the actual Material3 input and its label, not just search logic.
        // Incompatible Material3/Foundation style APIs fail during this measure pass.
        renderComposeScene(width = width, height = 350) {
            MaterialTheme {
                PdfSearchControls(
                    query = query,
                    onQueryChange = {},
                    matchCase = false,
                    onMatchCaseChange = {},
                    matchWholeWord = false,
                    onMatchWholeWordChange = {},
                    status = PdfViewSearchStatus.Idle,
                    selectedResultIndex = -1,
                    resultCount = 0,
                    canSearch = query.isNotEmpty(),
                    onSearch = {},
                    onClear = {},
                    onPrevious = {},
                    onNext = {},
                )
            }
        }.close()
    }
}
