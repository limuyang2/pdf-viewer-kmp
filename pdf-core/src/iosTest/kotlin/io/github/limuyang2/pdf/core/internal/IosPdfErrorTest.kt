package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfIncorrectPasswordException
import io.github.limuyang2.pdf.core.PdfInvalidFormatException
import io.github.limuyang2.pdf.core.PdfIoException
import io.github.limuyang2.pdf.core.PdfNativeException
import io.github.limuyang2.pdf.core.PdfPasswordRequiredException
import io.github.limuyang2.pdf.core.PdfUnsupportedSecurityException
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
