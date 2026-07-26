package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_DOCUMENT
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.Pinned

@OptIn(ExperimentalForeignApi::class)
internal class IosPdfDocument(
    val document: FPDF_DOCUMENT,
    val source: ByteArray,
    val pinnedSource: Pinned<ByteArray>,
)
