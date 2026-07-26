package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfIncorrectPasswordException
import io.github.limuyang2.pdf.viewer.PdfInvalidFormatException
import io.github.limuyang2.pdf.viewer.PdfIoException
import io.github.limuyang2.pdf.viewer.PdfNativeException
import io.github.limuyang2.pdf.viewer.PdfPasswordRequiredException
import io.github.limuyang2.pdf.viewer.PdfUnsupportedSecurityException

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
