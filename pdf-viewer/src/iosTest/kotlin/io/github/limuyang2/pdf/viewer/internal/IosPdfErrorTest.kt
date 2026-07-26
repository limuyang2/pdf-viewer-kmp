@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfIncorrectPasswordException
import io.github.limuyang2.pdf.viewer.PdfInvalidFormatException
import io.github.limuyang2.pdf.viewer.PdfIoException
import io.github.limuyang2.pdf.viewer.PdfNativeException
import io.github.limuyang2.pdf.viewer.PdfPasswordRequiredException
import io.github.limuyang2.pdf.viewer.PdfUnsupportedSecurityException
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_ERR_FILE
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_ERR_FORMAT
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_ERR_PASSWORD
import io.github.limuyang2.pdf.viewer.internal.pdfium.FPDF_ERR_SECURITY
import kotlin.test.Test
import kotlin.test.assertIs

internal class IosPdfErrorTest {
    @Test
    fun mapsDocumentOpenErrors() {
        assertIs<PdfIoException>(
            pdfiumOpenFailure(FPDF_ERR_FILE.toULong(), false),
        )
        assertIs<PdfInvalidFormatException>(
            pdfiumOpenFailure(FPDF_ERR_FORMAT.toULong(), false),
        )
        assertIs<PdfPasswordRequiredException>(
            pdfiumOpenFailure(FPDF_ERR_PASSWORD.toULong(), false),
        )
        assertIs<PdfIncorrectPasswordException>(
            pdfiumOpenFailure(FPDF_ERR_PASSWORD.toULong(), true),
        )
        assertIs<PdfUnsupportedSecurityException>(
            pdfiumOpenFailure(FPDF_ERR_SECURITY.toULong(), false),
        )
        assertIs<PdfNativeException>(
            pdfiumOpenFailure(999uL, false),
        )
    }
}
