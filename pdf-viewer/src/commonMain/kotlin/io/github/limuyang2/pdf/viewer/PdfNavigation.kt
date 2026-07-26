package io.github.limuyang2.pdf.viewer

public data class PdfDestination(
    val pageIndex: Int,
    val x: Double?,
    val y: Double?,
    val zoom: Double?,
) {
    init {
        require(pageIndex >= 0) { "pageIndex must be non-negative" }
        require(x == null || x.isFinite()) { "x must be null or finite" }
        require(y == null || y.isFinite()) { "y must be null or finite" }
        require(zoom == null || zoom.isFinite() && zoom > 0.0) {
            "zoom must be null or finite and positive"
        }
    }
}

public sealed interface PdfLinkTarget {
    public data class Internal(
        val destination: PdfDestination,
    ) : PdfLinkTarget

    public data class Uri(
        val uri: String,
    ) : PdfLinkTarget {
        init {
            require(uri.isNotBlank()) { "uri must not be blank" }
        }
    }

    public data class RemoteDocument(
        val filePath: String?,
        val destination: PdfDestination?,
    ) : PdfLinkTarget

    public data class Unsupported(
        val nativeActionType: Int,
    ) : PdfLinkTarget
}

public data class PdfLink(
    val bounds: List<PdfQuad>,
    val target: PdfLinkTarget,
)

public data class PdfBookmark(
    val title: String,
    val destination: PdfDestination?,
    val target: PdfLinkTarget?,
    val color: PdfColor?,
    val children: List<PdfBookmark>,
)
