package io.github.limuyang2.pdf.core

/**
 * Base class of every failure this library throws for expected PDF errors.
 */
sealed class PdfException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * The document is encrypted and no password was supplied to [PdfViewer.open].
 */
class PdfPasswordRequiredException :
    PdfException("The PDF document requires a password")

/**
 * The password supplied to [PdfViewer.open] did not decrypt the document.
 */
class PdfIncorrectPasswordException :
    PdfException("The supplied PDF password is incorrect")

/** The input is not a valid PDF document. */
class PdfInvalidFormatException :
    PdfException("The input is not a valid PDF document")

/**
 * The document uses a security scheme the bundled PDFium build cannot open.
 */
class PdfUnsupportedSecurityException :
    PdfException("The PDF document uses an unsupported security scheme")

/** A read failure occurred while loading the document. */
class PdfIoException(
    message: String = "Could not read the PDF document",
    cause: Throwable? = null,
) : PdfException(message, cause)

/**
 * A page-level operation failed.
 *
 * @property pageIndex zero-based index of the page that failed.
 */
class PdfPageException(
    val pageIndex: Int,
    cause: Throwable? = null,
) : PdfException("Could not process PDF page $pageIndex", cause) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
    }
}

/**
 * A document or bitmap was used after being closed.
 *
 * @property resource human-readable name of the closed resource.
 */
class PdfClosedException(
    val resource: String,
) : PdfException("$resource is already closed") {
    init {
        require(resource.isNotBlank()) { "resource must not be blank" }
    }
}

/**
 * An operation that the current backends do not implement was requested.
 * Check [PdfViewer.capabilities] before calling optional APIs.
 *
 * @property feature short name of the unavailable feature.
 */
class PdfUnsupportedFeatureException(
    val feature: String,
) : PdfException("Unsupported PDF feature: $feature") {
    init {
        require(feature.isNotBlank()) { "feature must not be blank" }
    }
}

/**
 * PDFium returned a native error that is not mapped to a more specific
 * exception.
 *
 * @property nativeErrorCode raw PDFium error code.
 */
class PdfNativeException(
    val nativeErrorCode: Int,
    message: String = "PDFium failed with error code $nativeErrorCode",
    cause: Throwable? = null,
) : PdfException(message, cause)
