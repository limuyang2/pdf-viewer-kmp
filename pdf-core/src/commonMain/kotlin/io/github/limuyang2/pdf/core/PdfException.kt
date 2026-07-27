package io.github.limuyang2.pdf.core

sealed class PdfException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class PdfPasswordRequiredException :
    PdfException("The PDF document requires a password")

class PdfIncorrectPasswordException :
    PdfException("The supplied PDF password is incorrect")

class PdfInvalidFormatException :
    PdfException("The input is not a valid PDF document")

class PdfUnsupportedSecurityException :
    PdfException("The PDF document uses an unsupported security scheme")

class PdfIoException(
    message: String = "Could not read the PDF document",
    cause: Throwable? = null,
) : PdfException(message, cause)

class PdfPageException(
    val pageIndex: Int,
    cause: Throwable? = null,
) : PdfException("Could not process PDF page $pageIndex", cause) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
    }
}

class PdfClosedException(
    val resource: String,
) : PdfException("$resource is already closed") {
    init {
        require(resource.isNotBlank()) { "resource must not be blank" }
    }
}

class PdfUnsupportedFeatureException(
    val feature: String,
) : PdfException("Unsupported PDF feature: $feature") {
    init {
        require(feature.isNotBlank()) { "feature must not be blank" }
    }
}

class PdfNativeException(
    val nativeErrorCode: Int,
    message: String = "PDFium failed with error code $nativeErrorCode",
    cause: Throwable? = null,
) : PdfException(message, cause)
