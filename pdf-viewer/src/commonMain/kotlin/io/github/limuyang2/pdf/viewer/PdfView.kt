package io.github.limuyang2.pdf.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.limuyang2.pdf.core.PdfDocument
import io.github.limuyang2.pdf.core.PdfLink
import io.github.limuyang2.pdf.core.PdfLinkTarget
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfPoint
import io.github.limuyang2.pdf.core.PdfQuad
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfRotation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Displays every page in [document] as a vertically scrolling Compose view.
 *
 * The caller retains ownership of [document]. [PdfView] closes temporary
 * rendered bitmaps, but never closes the document itself.
 *
 * [onLinkClick] runs before the built-in link behavior. Return `true` to
 * consume the link. Returning `false` lets [PdfView] navigate internal
 * destinations or open URI links through the platform URI handler.
 */
@Composable
public fun PdfView(
    document: PdfDocument,
    modifier: Modifier = Modifier,
    state: PdfViewState = rememberPdfViewState(),
    pageSpacing: Dp = 12.dp,
    pagePadding: Dp = 0.dp,
    pageColor: Color = Color.White,
    maxRenderDimension: Int = 4096,
    onPageError: (pageIndex: Int, error: Throwable) -> Unit = { _, _ -> },
    onLinkClick: (PdfLink) -> Boolean = { false },
) {
    require(pageSpacing >= 0.dp) { "pageSpacing must be non-negative" }
    require(pagePadding >= 0.dp) { "pagePadding must be non-negative" }
    require(maxRenderDimension > 0) {
        "maxRenderDimension must be positive"
    }

    state.bind(document)
    var viewportWidthPixels by remember { mutableIntStateOf(0) }
    var settledRenderWidthPixels by
        remember(maxRenderDimension) {
            mutableIntStateOf(0)
        }
    var transformInProgress by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    fun activateLink(
        pageIndex: Int,
        link: PdfLink,
    ) {
        if (onLinkClick(link)) {
            return
        }
        when (val target = link.target) {
            is PdfLinkTarget.Internal -> {
                val targetPage = target.destination.pageIndex
                if (targetPage !in 0 until document.pageCount) {
                    onPageError(
                        pageIndex,
                        IllegalArgumentException(
                            "PDF link targets invalid page $targetPage",
                        ),
                    )
                    return
                }
                coroutineScope.launch {
                    state.scrollToPage(targetPage)
                }
            }
            is PdfLinkTarget.Uri -> {
                try {
                    uriHandler.openUri(target.uri)
                } catch (failure: Throwable) {
                    onPageError(pageIndex, failure)
                }
            }
            is PdfLinkTarget.RemoteDocument,
            is PdfLinkTarget.Unsupported,
            -> Unit
        }
    }

    fun applyTransform(
        centroid: Offset,
        zoomChange: Float,
        panChange: Offset,
    ) {
        if (!zoomChange.isFinite() || zoomChange <= 0f) {
            return
        }
        val previousZoom = state.zoom
        state.zoomBy(zoomChange)
        val appliedZoom = state.zoom / previousZoom
        if (
            state.zoom > PdfViewState.MIN_ZOOM ||
            appliedZoom != 1f
        ) {
            state.horizontalScrollState.dispatchRawDelta(
                anchoredScrollDelta(
                    currentScroll =
                        state.horizontalScrollState.value.toFloat(),
                    centroid = centroid.x,
                    zoomChange = appliedZoom,
                    panChange = panChange.x,
                ),
            )
            state.listState.dispatchRawDelta(
                anchoredScrollDelta(
                    currentScroll =
                        state.listState.firstVisibleItemScrollOffset
                            .toFloat(),
                    centroid = centroid.y,
                    zoomChange = appliedZoom,
                    panChange = panChange.y,
                ),
            )
        }
    }

    Box(
        modifier =
            modifier
                .onSizeChanged { viewportWidthPixels = it.width }
                .pdfTransformGestures(
                    gestureKey = state,
                    onTransformStarted = {
                        transformInProgress = true
                    },
                    onTransform = ::applyTransform,
                    onTransformStopped = {
                        transformInProgress = false
                    },
                ),
    ) {
        if (viewportWidthPixels == 0) {
            Box(Modifier.fillMaxSize())
            return@Box
        }

        val density = LocalDensity.current
        val pagePaddingPixels = with(density) { pagePadding.roundToPx() }
        val contentWidthPixels =
            (viewportWidthPixels * state.zoom)
                .roundToInt()
                .coerceAtLeast(1)
        val displayedPageWidthPixels =
            (contentWidthPixels - pagePaddingPixels * 2)
                .coerceAtLeast(1)
        val contentWidth = with(density) { contentWidthPixels.toDp() }
        val pageWidth = with(density) { displayedPageWidthPixels.toDp() }
        val requestedRenderWidthPixels =
            quantizeRenderWidth(
                width = displayedPageWidthPixels,
                maximum = maxRenderDimension,
            )

        LaunchedEffect(requestedRenderWidthPixels, transformInProgress) {
            if (settledRenderWidthPixels == 0) {
                settledRenderWidthPixels = requestedRenderWidthPixels
            } else if (
                !transformInProgress &&
                settledRenderWidthPixels != requestedRenderWidthPixels
            ) {
                delay(RENDER_SETTLE_DELAY_MILLIS)
                settledRenderWidthPixels = requestedRenderWidthPixels
            }
        }
        val pageWidthPixels =
            settledRenderWidthPixels.takeIf { it > 0 }
                ?: requestedRenderWidthPixels

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .horizontalScroll(state.horizontalScrollState),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.width(contentWidth),
                state = state.listState,
                contentPadding = PaddingValues(pagePadding),
                verticalArrangement = Arrangement.spacedBy(pageSpacing),
            ) {
                items(
                    count = document.pageCount,
                    key = { pageIndex -> pageIndex },
                ) { pageIndex ->
                    PdfPage(
                        document = document,
                        pageIndex = pageIndex,
                        width = pageWidth,
                        renderWidth = pageWidthPixels,
                        state = state,
                        pageColor = pageColor,
                        maxRenderDimension = maxRenderDimension,
                        onError = onPageError,
                        onLinkActivated = { link ->
                            activateLink(pageIndex, link)
                        },
                    )
                }
            }
        }
    }
}

private fun Modifier.pdfTransformGestures(
    gestureKey: Any?,
    onTransformStarted: () -> Unit,
    onTransform: (centroid: Offset, zoomChange: Float, panChange: Offset) -> Unit,
    onTransformStopped: () -> Unit,
): Modifier =
    pointerInput(gestureKey) {
        awaitEachGesture {
            var claimedByTransform = false
            var transforming = false
            var hasPressedPointers: Boolean
            try {
                do {
                    val event =
                        awaitPointerEvent(PointerEventPass.Initial)
                    val pressedCount =
                        event.changes.count { it.pressed }

                    if (pressedCount >= MIN_TRANSFORM_POINTERS) {
                        claimedByTransform = true
                        if (!transforming) {
                            transforming = true
                            onTransformStarted()
                        }
                        onTransform(
                            event.calculateCentroid(useCurrent = false),
                            event.calculateZoom(),
                            event.calculatePan(),
                        )
                    } else if (transforming) {
                        transforming = false
                        onTransformStopped()
                    }

                    if (claimedByTransform) {
                        event.changes.forEach { it.consume() }
                    }
                    hasPressedPointers =
                        event.changes.any { it.pressed }
                } while (hasPressedPointers)
            } finally {
                if (transforming) {
                    onTransformStopped()
                }
            }
        }
    }

internal fun anchoredScrollDelta(
    currentScroll: Float,
    centroid: Float,
    zoomChange: Float,
    panChange: Float,
): Float =
    (currentScroll + centroid) * (zoomChange - 1f) -
        panChange

@Composable
private fun PdfPage(
    document: PdfDocument,
    pageIndex: Int,
    width: Dp,
    renderWidth: Int,
    state: PdfViewState,
    pageColor: Color,
    maxRenderDimension: Int,
    onError: (Int, Throwable) -> Unit,
    onLinkActivated: (PdfLink) -> Unit,
) {
    val informationState by
        produceState<PdfPageInformationState>(
            initialValue = PdfPageInformationState.Loading,
            document,
            pageIndex,
        ) {
            value =
                try {
                    val information = document[pageIndex].information()
                    check(
                        information.size.width > 0.0 &&
                            information.size.height > 0.0,
                    ) {
                        "PDF page ${pageIndex + 1} has an invalid size"
                    }
                    PdfPageInformationState.Ready(information)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    onError(pageIndex, failure)
                    PdfPageInformationState.Failed
                }
        }
    val pageInformation =
        when (val current = informationState) {
            PdfPageInformationState.Loading -> {
                PdfPagePlaceholder(
                    width = width,
                    aspectRatio = DEFAULT_PAGE_ASPECT_RATIO,
                    pageColor = pageColor,
                )
                return
            }
            PdfPageInformationState.Failed -> {
                PdfPageError(
                    width = width,
                    aspectRatio = DEFAULT_PAGE_ASPECT_RATIO,
                    pageIndex = pageIndex,
                    pageColor = pageColor,
                )
                return
            }
            is PdfPageInformationState.Ready -> current.information
        }
    val links by
        produceState(
            initialValue = emptyList<PdfLink>(),
            document,
            pageIndex,
        ) {
            value =
                try {
                    document[pageIndex].links()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: Throwable) {
                    onError(pageIndex, failure)
                    emptyList()
                }
        }
    val currentOnLinkActivated by
        rememberUpdatedState(onLinkActivated)

    val aspectRatio =
        pageInformation.size.width.toFloat() /
            pageInformation.size.height.toFloat()
    val renderSize =
        remember(pageInformation, renderWidth, maxRenderDimension) {
            calculateRenderSize(
                pageInformation = pageInformation,
                requestedWidth = renderWidth,
                maximumDimension = maxRenderDimension,
            )
        }
    val cacheKey =
        remember(pageIndex, renderSize) {
            PdfRenderCacheKey(
                pageIndex = pageIndex,
                width = renderSize.width,
                height = renderSize.height,
            )
        }
    var renderState by
        remember(document, pageIndex) {
            mutableStateOf<PdfPageRenderState>(
                state
                    .cachedImage(document, cacheKey)
                    ?.let(PdfPageRenderState::Ready)
                    ?: PdfPageRenderState.Loading,
            )
        }

    LaunchedEffect(document, cacheKey) {
        state.cachedImage(document, cacheKey)?.let {
            renderState = PdfPageRenderState.Ready(it)
            return@LaunchedEffect
        }
        if (renderState !is PdfPageRenderState.Ready) {
            renderState = PdfPageRenderState.Loading
        }
        try {
            val image =
                withContext(Dispatchers.Default) {
                    val bitmap =
                        document[pageIndex].render(
                            PdfRenderRequest(outputSize = renderSize),
                        )
                    try {
                        bitmap.toComposeImageBitmap()
                    } finally {
                        bitmap.close()
                    }
                }
            state.cacheImage(document, cacheKey, image)
            renderState = PdfPageRenderState.Ready(image)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            if (renderState !is PdfPageRenderState.Ready) {
                renderState = PdfPageRenderState.Failed
            }
            onError(pageIndex, failure)
        }
    }

    Box(
        modifier =
            Modifier
                .width(width)
                .aspectRatio(aspectRatio)
                .background(pageColor)
                .border(1.dp, Color(0x22000000))
                .pointerInput(links, pageInformation) {
                    detectTapGestures { position ->
                        findPdfLinkAt(
                            links = links,
                            position = position,
                            displayedWidth = size.width.toFloat(),
                            displayedHeight = size.height.toFloat(),
                            pageInformation = pageInformation,
                        )?.let(currentOnLinkActivated)
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        when (val current = renderState) {
            PdfPageRenderState.Loading -> Unit
            is PdfPageRenderState.Ready ->
                Image(
                    bitmap = current.image,
                    contentDescription = "PDF page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            PdfPageRenderState.Failed ->
                BasicText(
                    text = "Page ${pageIndex + 1} could not be rendered",
                    style = TextStyle(color = Color(0xff991b1b)),
                )
        }
    }
}

internal fun findPdfLinkAt(
    links: List<PdfLink>,
    position: Offset,
    displayedWidth: Float,
    displayedHeight: Float,
    pageInformation: PdfPageInfo,
): PdfLink? {
    if (
        displayedWidth <= 0f ||
        displayedHeight <= 0f ||
        position == Offset.Unspecified
    ) {
        return null
    }
    val point =
        displayedPointToPdf(
            position = position,
            displayedWidth = displayedWidth,
            displayedHeight = displayedHeight,
            pageInformation = pageInformation,
        )
    return links.lastOrNull { link ->
        link.bounds.any { it.contains(point) }
    }
}

internal fun displayedPointToPdf(
    position: Offset,
    displayedWidth: Float,
    displayedHeight: Float,
    pageInformation: PdfPageInfo,
): PdfPoint {
    val displayedPageWidth = pageInformation.size.width
    val displayedPageHeight = pageInformation.size.height
    val displayedX =
        position.x / displayedWidth * displayedPageWidth
    val displayedY =
        position.y / displayedHeight * displayedPageHeight
    val nativeWidth =
        when (pageInformation.rotation) {
            PdfRotation.Degrees0,
            PdfRotation.Degrees180,
            -> displayedPageWidth
            PdfRotation.Degrees90,
            PdfRotation.Degrees270,
            -> displayedPageHeight
        }
    val nativeHeight =
        when (pageInformation.rotation) {
            PdfRotation.Degrees0,
            PdfRotation.Degrees180,
            -> displayedPageHeight
            PdfRotation.Degrees90,
            PdfRotation.Degrees270,
            -> displayedPageWidth
        }
    return when (pageInformation.rotation) {
        PdfRotation.Degrees0 ->
            PdfPoint(
                x = displayedX,
                y = nativeHeight - displayedY,
            )
        PdfRotation.Degrees90 ->
            PdfPoint(
                x = displayedY,
                y = displayedX,
            )
        PdfRotation.Degrees180 ->
            PdfPoint(
                x = nativeWidth - displayedX,
                y = displayedY,
            )
        PdfRotation.Degrees270 ->
            PdfPoint(
                x = nativeWidth - displayedY,
                y = nativeHeight - displayedX,
            )
    }
}

private fun PdfQuad.contains(point: PdfPoint): Boolean {
    val points = listOf(first, second, third, fourth)
    val left = points.minOf(PdfPoint::x)
    val right = points.maxOf(PdfPoint::x)
    val bottom = points.minOf(PdfPoint::y)
    val top = points.maxOf(PdfPoint::y)
    return point.x in left..right && point.y in bottom..top
}

@Composable
private fun PdfPageError(
    width: Dp,
    aspectRatio: Float,
    pageIndex: Int,
    pageColor: Color,
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .aspectRatio(aspectRatio)
                .background(pageColor)
                .border(1.dp, Color(0x22000000)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Page ${pageIndex + 1} could not be loaded",
            style = TextStyle(color = Color(0xff991b1b)),
        )
    }
}

@Composable
private fun PdfPagePlaceholder(
    width: Dp,
    aspectRatio: Float,
    pageColor: Color,
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .aspectRatio(aspectRatio)
                .background(pageColor)
                .border(1.dp, Color(0x22000000)),
    )
}

internal fun calculateRenderSize(
    pageInformation: PdfPageInfo,
    requestedWidth: Int,
    maximumDimension: Int,
): PdfPixelSize {
    require(requestedWidth > 0) { "requestedWidth must be positive" }
    require(maximumDimension > 0) {
        "maximumDimension must be positive"
    }
    val ratio =
        pageInformation.size.height / pageInformation.size.width
    require(ratio.isFinite() && ratio > 0.0) {
        "page size must have positive width and height"
    }
    var width = requestedWidth.coerceAtMost(maximumDimension)
    var height = ceil(width * ratio).toInt().coerceAtLeast(1)
    if (height > maximumDimension) {
        height = maximumDimension
        width =
            ceil(height / ratio)
                .toInt()
                .coerceIn(1, maximumDimension)
    }
    return PdfPixelSize(width, height)
}

internal fun quantizeRenderWidth(
    width: Int,
    maximum: Int,
): Int {
    require(width > 0) { "width must be positive" }
    require(maximum > 0) { "maximum must be positive" }
    val quantized =
        ceil(width.toDouble() / RENDER_WIDTH_QUANTUM)
            .roundToInt() * RENDER_WIDTH_QUANTUM
    return quantized.coerceIn(1, maximum)
}

private sealed interface PdfPageRenderState {
    data object Loading : PdfPageRenderState

    data class Ready(
        val image: ImageBitmap,
    ) : PdfPageRenderState

    data object Failed : PdfPageRenderState
}

private sealed interface PdfPageInformationState {
    data object Loading : PdfPageInformationState

    data class Ready(
        val information: PdfPageInfo,
    ) : PdfPageInformationState

    data object Failed : PdfPageInformationState
}

private const val RENDER_WIDTH_QUANTUM: Int = 128
private const val RENDER_SETTLE_DELAY_MILLIS: Long = 150
private const val MIN_TRANSFORM_POINTERS: Int = 2
private const val DEFAULT_PAGE_ASPECT_RATIO: Float = 0.707f
