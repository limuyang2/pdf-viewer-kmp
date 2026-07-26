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

internal fun pdfiumOpenFailure(
    errorCode: ULong,
    passwordWasSupplied: Boolean,
): Throwable =
    when (errorCode) {
        FPDF_ERR_FILE.toULong() -> PdfIoException()
        FPDF_ERR_FORMAT.toULong() -> PdfInvalidFormatException()
        FPDF_ERR_PASSWORD.toULong() ->
            if (passwordWasSupplied) {
                PdfIncorrectPasswordException()
            } else {
                PdfPasswordRequiredException()
            }
        FPDF_ERR_SECURITY.toULong() -> PdfUnsupportedSecurityException()
        else -> PdfNativeException(errorCode.toInt())
    }
