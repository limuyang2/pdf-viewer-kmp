package io.github.limuyang2.pdf.core

public sealed class PdfException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

public class PdfPasswordRequiredException :
    PdfException("The PDF document requires a password")

public class PdfIncorrectPasswordException :
    PdfException("The supplied PDF password is incorrect")

public class PdfInvalidFormatException :
    PdfException("The input is not a valid PDF document")

public class PdfUnsupportedSecurityException :
    PdfException("The PDF document uses an unsupported security scheme")

public class PdfIoException(
    message: String = "Could not read the PDF document",
    cause: Throwable? = null,
) : PdfException(message, cause)

public class PdfPageException(
    public val pageIndex: Int,
    cause: Throwable? = null,
) : PdfException("Could not process PDF page $pageIndex", cause) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
    }
}

public class PdfClosedException(
    public val resource: String,
) : PdfException("$resource is already closed") {
    init {
        require(resource.isNotBlank()) { "resource must not be blank" }
    }
}

public class PdfUnsupportedFeatureException(
    public val feature: String,
) : PdfException("Unsupported PDF feature: $feature") {
    init {
        require(feature.isNotBlank()) { "feature must not be blank" }
    }
}

public class PdfNativeException(
    public val nativeErrorCode: Int,
    message: String = "PDFium failed with error code $nativeErrorCode",
    cause: Throwable? = null,
) : PdfException(message, cause)
