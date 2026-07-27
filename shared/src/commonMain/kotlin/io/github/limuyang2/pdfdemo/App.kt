package io.github.limuyang2.pdfdemo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.github.limuyang2.pdf.core.PdfDocument
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import io.github.limuyang2.pdf.viewer.PdfView
import io.github.limuyang2.pdf.viewer.rememberPdfViewState
import pdfdemo.shared.generated.resources.Res

@Composable
public fun App() {
    MaterialTheme {
        val backStack =
            remember {
                mutableStateListOf<Any>(PdfSamplesRoute)
            }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = {
                    if (backStack.size > 1) {
                        backStack.removeLastOrNull()
                    }
                },
                entryProvider = { key ->
                    when (key) {
                        PdfSamplesRoute ->
                            NavEntry(key) {
                                PdfSamplesScreen(
                                    onOpenPdf = backStack::add,
                                )
                            }
                        is PdfRoute ->
                            NavEntry(key) {
                                PdfDocumentScreen(
                                    route = key,
                                    onBack = {
                                        backStack.removeLastOrNull()
                                    },
                                )
                            }
                        else -> error("Unknown navigation key: $key")
                    }
                },
            )
        }
    }
}

@Composable
private fun PdfSamplesScreen(
    onOpenPdf: (PdfRoute) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "PDF Viewer Demo",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "选择一个 Navigation 3 路由页面：",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PdfSampleButton(
            title = "Dummy · 3 pages",
            description = "基础三页 PDF",
            onClick = {
                onOpenPdf(
                    PdfRoute(
                        title = "Dummy · 3 pages",
                        resourcePath = DUMMY_PDF_RESOURCE,
                    ),
                )
            },
        )
        PdfSampleButton(
            title = "Text, images & links · 2 pages",
            description = "包含文本、图片和链接的 PDF",
            onClick = {
                onOpenPdf(
                    PdfRoute(
                        title = "Text, images & links · 2 pages",
                        resourcePath = SAMPLE_PDF_RESOURCE,
                    ),
                )
            },
        )
        PdfSampleButton(
            title = "Broken PDF · loading error",
            description = "故意损坏的 PDF，用于测试加载错误页面",
            onClick = {
                onOpenPdf(
                    PdfRoute(
                        title = "Broken PDF · loading error",
                        resourcePath = BROKEN_PDF_RESOURCE,
                    ),
                )
            },
        )
    }
}

@Composable
private fun PdfSampleButton(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PdfDocumentScreen(
    route: PdfRoute,
    onBack: () -> Unit,
) {
    var document by
        remember(route.resourcePath) {
            mutableStateOf<PdfDocument?>(null)
        }
    var failure by
        remember(route.resourcePath) {
            mutableStateOf<Throwable?>(null)
        }
    val viewState = rememberPdfViewState()

    LaunchedEffect(route.resourcePath) {
        try {
            document =
                PdfViewer.open(
                    PdfSource.Bytes(
                        Res.readBytes(route.resourcePath),
                    ),
                )
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

    Column(Modifier.fillMaxSize()) {
        PdfToolbar(
            title = route.title,
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

        when {
            document != null ->
                PdfView(
                    document = checkNotNull(document),
                    state = viewState,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color(0xffe5e7eb)),
                    pageLoadingContent = {
                        CircularProgressIndicator()
                    },
                    pageErrorContent = { pageIndex, error ->
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "第 ${pageIndex + 1} 页加载失败",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text =
                                    error.message
                                        ?: error::class.simpleName
                                        ?: "Unknown error",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
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
private fun PdfToolbar(
    title: String,
    document: PdfDocument?,
    currentPage: Int,
    zoom: Float,
    onBack: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top +
                            WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) {
            Text("返回")
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        document?.let {
            Text(
                text = "${currentPage + 1} / ${it.pageCount}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
        IconButton(
            onClick = onZoomOut,
            enabled = zoom > 1f,
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        Text(
            text = "${(zoom * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge,
        )
        IconButton(
            onClick = onZoomIn,
            enabled = zoom < 4f,
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun PdfOpenError(
    failure: Throwable,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "PDF 加载失败",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    failure.message
                        ?: failure::class.simpleName
                        ?: "Unknown error",
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

private data object PdfSamplesRoute

private data class PdfRoute(
    val title: String,
    val resourcePath: String,
)

private const val DUMMY_PDF_RESOURCE: String =
    "files/pdfs/dummy-3-pages.pdf"
private const val SAMPLE_PDF_RESOURCE: String =
    "files/pdfs/sample-text-images-links-2-pages.pdf"
private const val BROKEN_PDF_RESOURCE: String =
    "files/pdfs/broken-loading-error.pdf"
