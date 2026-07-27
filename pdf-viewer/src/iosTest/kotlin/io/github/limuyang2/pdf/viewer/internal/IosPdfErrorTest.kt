package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfIncorrectPasswordException
import io.github.limuyang2.pdf.viewer.PdfInvalidFormatException
import io.github.limuyang2.pdf.viewer.PdfIoException
import io.github.limuyang2.pdf.viewer.PdfNativeException
import io.github.limuyang2.pdf.viewer.PdfPasswordRequiredException
import io.github.limuyang2.pdf.viewer.PdfUnsupportedSecurityException
import kotlin.test.Test
import kotlin.test.assertIs

internal class IosPdfErrorTest {
    @Test
    fun mapsDocumentOpenErrors() {
        assertIs<PdfIoException>(
            pdfiumOpenFailure(2uL, false),
        )
        assertIs<PdfInvalidFormatException>(
            pdfiumOpenFailure(3uL, false),
        )
        assertIs<PdfPasswordRequiredException>(
            pdfiumOpenFailure(4uL, false),
        )
        assertIs<PdfIncorrectPasswordException>(
            pdfiumOpenFailure(4uL, true),
        )
        assertIs<PdfUnsupportedSecurityException>(
            pdfiumOpenFailure(5uL, false),
        )
        assertIs<PdfNativeException>(
            pdfiumOpenFailure(999uL, false),
        )
    }
}
