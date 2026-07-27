package io.github.limuyang2.pdfdemo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.limuyang2.pdf.core.PdfDocument
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import io.github.limuyang2.pdf.viewer.PdfView
import io.github.limuyang2.pdf.viewer.rememberPdfViewState

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
                        PdfSource.Bytes(createSamplePdf(pageCount = 5)),
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .safeContentPadding(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                            modifier = Modifier.fillMaxSize(),
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

private fun createSamplePdf(pageCount: Int): ByteArray {
    require(pageCount > 0)
    val pageObjectNumbers = (0 until pageCount).map { 3 + it * 2 }
    val objects = mutableListOf<String>()
    objects += "<< /Type /Catalog /Pages 2 0 R >>"
    objects +=
        "<< /Type /Pages /Kids [" +
        pageObjectNumbers.joinToString(" ") { "$it 0 R" } +
        "] /Count $pageCount >>"
    repeat(pageCount) { pageIndex ->
        val pageObject = 3 + pageIndex * 2
        val contentObject = pageObject + 1
        val stream =
            "BT /F1 26 Tf 72 720 Td " +
                "(PDF Viewer KMP - Page ${pageIndex + 1}) Tj " +
                "0 -44 Td /F1 14 Tf " +
                "(Android / iOS / JVM / JS / Wasm) Tj ET"
        objects +=
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
            "/Resources << /Font << /F1 ${3 + pageCount * 2} 0 R >> >> " +
            "/Contents $contentObject 0 R >>"
        objects +=
            "<< /Length ${stream.length} >>\nstream\n$stream\nendstream"
    }
    objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"

    val output = StringBuilder("%PDF-1.7\n")
    val offsets = mutableListOf<Int>()
    objects.forEachIndexed { index, body ->
        offsets += output.length
        output.append("${index + 1} 0 obj\n$body\nendobj\n")
    }
    val xrefOffset = output.length
    output.append("xref\n0 ${objects.size + 1}\n")
    output.append("0000000000 65535 f \n")
    offsets.forEach { offset ->
        output.append(offset.toString().padStart(10, '0'))
        output.append(" 00000 n \n")
    }
    output.append(
        "trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n" +
            "startxref\n$xrefOffset\n%%EOF\n",
    )
    return output.toString().encodeToByteArray()
}
