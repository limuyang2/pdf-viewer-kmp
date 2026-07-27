package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfIncorrectPasswordException
import io.github.limuyang2.pdf.core.PdfInvalidFormatException
import io.github.limuyang2.pdf.core.PdfIoException
import io.github.limuyang2.pdf.core.PdfNativeException
import io.github.limuyang2.pdf.core.PdfPasswordRequiredException
import io.github.limuyang2.pdf.core.PdfUnsupportedSecurityException

private const val FPDF_ERR_FILE = 2L
private const val FPDF_ERR_FORMAT = 3L
private const val FPDF_ERR_PASSWORD = 4L
private const val FPDF_ERR_SECURITY = 5L

internal fun androidPdfiumOpenFailure(
    errorCode: Long,
    passwordWasSupplied: Boolean,
): Throwable =
    when (errorCode) {
        FPDF_ERR_FILE -> PdfIoException()
        FPDF_ERR_FORMAT -> PdfInvalidFormatException()
        FPDF_ERR_PASSWORD ->
            if (passwordWasSupplied) {
                PdfIncorrectPasswordException()
            } else {
                PdfPasswordRequiredException()
            }
        FPDF_ERR_SECURITY -> PdfUnsupportedSecurityException()
        else -> PdfNativeException(errorCode.toInt())
    }
