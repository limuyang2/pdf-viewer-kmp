package io.github.limuyang2.pdfdemo

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.limuyang2.pdf.core.PdfDocument
import io.github.limuyang2.pdf.core.PdfSearchOptions
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import io.github.limuyang2.pdf.viewer.PdfSearchHighlightDecoration
import io.github.limuyang2.pdf.viewer.PdfSearchHighlightStyle
import io.github.limuyang2.pdf.viewer.PdfView
import io.github.limuyang2.pdf.viewer.PdfViewSearchStatus
import io.github.limuyang2.pdf.viewer.rememberPdfViewState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pdfdemo.shared.generated.resources.Res
import pdfdemo.shared.generated.resources.match_case
import pdfdemo.shared.generated.resources.match_whole_word
import pdfdemo.shared.generated.resources.search_action
import pdfdemo.shared.generated.resources.search_clear
import pdfdemo.shared.generated.resources.search_completed
import pdfdemo.shared.generated.resources.search_demo_title
import pdfdemo.shared.generated.resources.search_failed
import pdfdemo.shared.generated.resources.search_idle
import pdfdemo.shared.generated.resources.search_next
import pdfdemo.shared.generated.resources.search_previous
import pdfdemo.shared.generated.resources.search_progress
import pdfdemo.shared.generated.resources.search_query_label
import pdfdemo.shared.generated.resources.search_result_position

@Composable
internal fun PdfSearchScreen(
    onBack: () -> Unit,
) {
    var document by remember {
        mutableStateOf<PdfDocument?>(null)
    }
    var failure by remember {
        mutableStateOf<Throwable?>(null)
    }
    var query by remember {
        mutableStateOf(DEFAULT_SEARCH_QUERY)
    }
    var matchCase by remember {
        mutableStateOf(false)
    }
    var matchWholeWord by remember {
        mutableStateOf(false)
    }
    var searchJob by remember {
        mutableStateOf<Job?>(null)
    }
    val viewState = rememberPdfViewState()
    val coroutineScope = rememberCoroutineScope()
    val highlightStyle =
        remember {
            PdfSearchHighlightStyle(
                match =
                    PdfSearchHighlightDecoration(
                        fillColor = Color(0x6638BDF8),
                        cornerRadius = 2.dp,
                        padding = 1.dp,
                    ),
                selectedMatch =
                    PdfSearchHighlightDecoration(
                        fillColor = Color(0x80FB923C),
                        strokeColor = Color(0xFFC2410C),
                        strokeWidth = 2.dp,
                        cornerRadius = 2.dp,
                        padding = 1.dp,
                    ),
            )
        }

    LaunchedEffect(Unit) {
        try {
            document =
                PdfViewer.open(
                    PdfSource.Bytes(
                        Res.readBytes(SAMPLE_PDF_RESOURCE),
                    ),
                )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (openFailure: Throwable) {
            failure = openFailure
        }
    }
    val currentDocument = document
    DisposableEffect(currentDocument) {
        onDispose {
            currentDocument?.close()
        }
    }

    fun startSearch() {
        val targetDocument = document ?: return
        searchJob?.cancel()
        searchJob =
            coroutineScope.launch {
                viewState.search(
                    document = targetDocument,
                    query = query,
                    options =
                        PdfSearchOptions(
                            matchCase = matchCase,
                            matchWholeWord = matchWholeWord,
                        ),
                )
                viewState.selectedSearchResult?.let { result ->
                    viewState.animateScrollToPage(result.pageIndex)
                }
            }
    }

    Column(Modifier.fillMaxSize()) {
        PdfToolbar(
            title = stringResource(Res.string.search_demo_title),
            document = document,
            currentPage = viewState.currentPage,
            zoom = viewState.zoom,
            onBack = onBack,
            onZoomOut = {
                viewState.updateZoom(viewState.zoom - 0.25f)
            },
            onZoomIn = {
                viewState.updateZoom(viewState.zoom + 0.25f)
            },
        )
        HorizontalDivider()
        PdfSearchControls(
            query = query,
            onQueryChange = { query = it },
            matchCase = matchCase,
            onMatchCaseChange = { matchCase = it },
            matchWholeWord = matchWholeWord,
            onMatchWholeWordChange = { matchWholeWord = it },
            status = viewState.searchStatus,
            selectedResultIndex = viewState.selectedSearchResultIndex,
            resultCount = viewState.searchResults.size,
            canSearch = document != null && query.isNotEmpty(),
            onSearch = ::startSearch,
            onClear = {
                searchJob?.cancel()
                query = ""
                viewState.clearSearch()
            },
            onPrevious = {
                coroutineScope.launch {
                    viewState
                        .selectPreviousSearchResult()
                        ?.let { result ->
                            viewState.animateScrollToPage(result.pageIndex)
                        }
                }
            },
            onNext = {
                coroutineScope.launch {
                    viewState
                        .selectNextSearchResult()
                        ?.let { result ->
                            viewState.animateScrollToPage(result.pageIndex)
                        }
                }
            },
        )
        HorizontalDivider()

        when {
            document != null ->
                PdfView(
                    document = checkNotNull(document),
                    state = viewState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xffe5e7eb)),
                    searchHighlightStyle = highlightStyle,
                    pageLoadingContent = {
                        CircularProgressIndicator()
                    },
                )

            failure != null ->
                PdfOpenError(
                    failure = checkNotNull(failure),
                )

            else ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
        }
    }
}

@Composable
private fun PdfSearchControls(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCase: Boolean,
    onMatchCaseChange: (Boolean) -> Unit,
    matchWholeWord: Boolean,
    onMatchWholeWordChange: (Boolean) -> Unit,
    status: PdfViewSearchStatus,
    selectedResultIndex: Int,
    resultCount: Int,
    canSearch: Boolean,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(stringResource(Res.string.search_query_label))
            },
            singleLine = true,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchOption(
                checked = matchCase,
                label = stringResource(Res.string.match_case),
                onCheckedChange = onMatchCaseChange,
            )
            SearchOption(
                checked = matchWholeWord,
                label = stringResource(Res.string.match_whole_word),
                onCheckedChange = onMatchWholeWordChange,
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onSearch,
                enabled = canSearch,
            ) {
                Text(stringResource(Res.string.search_action))
            }
            TextButton(onClick = onClear) {
                Text(stringResource(Res.string.search_clear))
            }
            TextButton(
                onClick = onPrevious,
                enabled = resultCount > 0,
            ) {
                Text(stringResource(Res.string.search_previous))
            }
            TextButton(
                onClick = onNext,
                enabled = resultCount > 0,
            ) {
                Text(stringResource(Res.string.search_next))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = searchStatusText(status),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (status is PdfViewSearchStatus.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            if (selectedResultIndex >= 0 && resultCount > 0) {
                Text(
                    text =
                        stringResource(
                            Res.string.search_result_position,
                            selectedResultIndex + 1,
                            resultCount,
                        ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SearchOption(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
        Text(label)
    }
}

@Composable
private fun searchStatusText(
    status: PdfViewSearchStatus,
): String =
    when (status) {
        PdfViewSearchStatus.Idle ->
            stringResource(Res.string.search_idle)
        is PdfViewSearchStatus.Searching ->
            stringResource(
                Res.string.search_progress,
                status.completedPageCount,
                status.totalPageCount,
            )
        is PdfViewSearchStatus.Completed ->
            stringResource(
                Res.string.search_completed,
                status.resultCount,
            )
        is PdfViewSearchStatus.Failed ->
            stringResource(
                Res.string.search_failed,
                status.error.message
                    ?: status.error::class.simpleName
                    ?: "",
            )
    }

private const val DEFAULT_SEARCH_QUERY: String = "PDF"
