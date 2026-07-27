package io.github.limuyang2.pdfdemo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.limuyang2.pdf.core.PdfDocument
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import io.github.limuyang2.pdf.viewer.PdfView
import io.github.limuyang2.pdf.viewer.rememberPdfViewState
import pdfdemo.shared.generated.resources.Res

@Composable
public fun App() {
    MaterialTheme {
        var document by remember { mutableStateOf<PdfDocument?>(null) }
        var failure by remember { mutableStateOf<Throwable?>(null) }
        val viewState = rememberPdfViewState()

        LaunchedEffect(Unit) {
            try {
                document =
                    PdfViewer.open(
                        PdfSource.Bytes(
                            Res.readBytes(SAMPLE_PDF_RESOURCE),
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

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier =
                        Modifier
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(
                                    WindowInsetsSides.Top +
                                        WindowInsetsSides.Horizontal,
                                ),
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "PDF Viewer",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    document?.let {
                        Text(
                            text = "${viewState.currentPage + 1} / ${it.pageCount}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    IconButton(
                        onClick = {
                            viewState.updateZoom(viewState.zoom - 0.25f)
                        },
                        enabled = viewState.zoom > 1f,
                    ) {
                        Text("−", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        text = "${(viewState.zoom * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    IconButton(
                        onClick = {
                            viewState.updateZoom(viewState.zoom + 0.25f)
                        },
                        enabled = viewState.zoom < 4f,
                    ) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
                HorizontalDivider()

                when {
                    document != null ->
                        PdfView(
                            document = checkNotNull(document),
                            state = viewState,
                            modifier = Modifier.fillMaxSize().background(Color(0xffe5e7eb)),
                        )
                    failure != null ->
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = failure?.message ?: "Could not open PDF",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
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
    }
}

private const val SAMPLE_PDF_RESOURCE: String =
    "files/pdfs/sample-text-images-links-2-pages.pdf"
