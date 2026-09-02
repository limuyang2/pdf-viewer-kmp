package io.github.limuyang2.pdf.viewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.limuyang2.pdf.core.PdfDocument
import io.github.limuyang2.pdf.core.PdfLink
import io.github.limuyang2.pdf.core.PdfLinkTarget
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfPoint
import io.github.limuyang2.pdf.core.PdfQuad
import io.github.limuyang2.pdf.core.PdfRect
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfRotation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Displays every page in [document] as a vertically scrolling Compose view.
 *
 * The caller retains ownership of [document]. [PdfView] closes temporary
 * rendered bitmaps, but never closes the document itself.
 *
 * [onLinkClick] runs before the built-in link behavior. Return `true` to
 * consume the link. Returning `false` lets [PdfView] navigate internal
 * destinations. By default, URI links open through the platform URI handler.
 * Pass `null` to [onUriLinkClick] to ignore them, or provide a callback to
 * replace the default behavior. Link activation failures are reported through
 * [onLinkError], separately from page loading and rendering failures.
 *
 * Set [pageBorder] to `null` to render pages without a border.
 * [pageLoadingContent] is shown while page information or imagery is
 * loading. [pageErrorContent] receives the original failure; when it is
 * `null`, [PdfView] displays its built-in error message.
 *
 * @param document The open PDF document to display. The caller remains
 * responsible for closing it.
 * @param modifier The modifier applied to the PDF viewport.
 * @param state The state that controls scrolling, zoom, and rendered-page
 * caching.
 * @param pageSpacing The vertical space between adjacent pages.
 * @param pagePadding The padding around the page list.
 * @param pageColor The background color shown behind each rendered page.
 * @param maxRenderDimension The maximum width or height, in pixels, of a
 * rendered page bitmap.
 * @param onPageError Called when page information or page rendering fails.
 * @param onLinkClick Called before built-in link handling. Return `true` to
 * consume the link.
 * @param pageBorder The border drawn around each page, or `null` for no
 * border.
 * @param pageLoadingContent Content displayed while a page is loading.
 * @param pageErrorContent Content displayed when a page fails to load or
 * render. When `null`, the built-in error content is used.
 * @param onUriLinkClick Handles URI links. Pass `null` to ignore URI links.
 * @param onLinkError Called when link activation fails.
 * @param maxZoom The maximum zoom multiplier for gesture-driven and
 * programmatic zoom while this view is bound to [state]. It must be finite
 * and at least `1f`.
 * @param gestureZoomEnabled Whether multi-touch zoom gestures are enabled.
 * Programmatic zoom through [state] remains available when disabled. Pan
 * deltas are ignored during a multi-touch zoom to avoid competing with scroll.
 * @param searchHighlightStyle Configures normal and selected search-result
 * highlights.
 */
@Composable
fun PdfView(
    document: PdfDocument,
    modifier: Modifier = Modifier,
    state: PdfViewState = rememberPdfViewState(),
    pageSpacing: Dp = 12.dp,
    pagePadding: Dp = 0.dp,
    pageColor: Color = Color.White,
    maxRenderDimension: Int = 4096,
    onPageError: (pageIndex: Int, error: Throwable) -> Unit = { _, _ -> },
    onLinkClick: (PdfLink) -> Boolean = { false },
    pageBorder: BorderStroke? = BorderStroke(1.dp, Color(0x22000000)),
    pageLoadingContent: @Composable BoxScope.(pageIndex: Int) -> Unit = {},
    pageErrorContent: (@Composable BoxScope.(pageIndex: Int, error: Throwable) -> Unit)? = null,
    onUriLinkClick: ((uri: String) -> Unit)? = DefaultOnUriLinkClick,
    onLinkError: (pageIndex: Int, link: PdfLink, error: Throwable) -> Unit = { _, _, _ -> },
    maxZoom: Float = PdfViewState.DEFAULT_MAX_ZOOM,
    gestureZoomEnabled: Boolean = true,
    searchHighlightStyle: PdfSearchHighlightStyle = PdfSearchHighlightStyle.Default,
) {
    require(!document.isClosed) {
        "PdfView requires an open PdfDocument"
    }
    require(pageSpacing >= 0.dp) { "pageSpacing must be non-negative" }
    require(pagePadding >= 0.dp) { "pagePadding must be non-negative" }
    require(maxRenderDimension > 0) {
        "maxRenderDimension must be positive"
    }
    require(maxZoom.isFinite() && maxZoom >= PdfViewState.MIN_ZOOM) {
        "maxZoom must be finite and at least ${PdfViewState.MIN_ZOOM}"
    }

    SideEffect {
        state.bind(document, maxZoom)
    }
    var viewportSizePixels by remember {
        mutableStateOf(IntSize.Zero)
    }
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
                    onLinkError(
                        pageIndex,
                        link,
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
                if (onUriLinkClick != null) {
                    try {
                        if (onUriLinkClick === DefaultOnUriLinkClick) {
                            uriHandler.openUri(target.uri)
                        } else {
                            onUriLinkClick(target.uri)
                        }
                    } catch (failure: Throwable) {
                        onLinkError(pageIndex, link, failure)
                    }
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
                    panChange = 0f,
                ),
            )
            state.listState.dispatchRawDelta(
                anchoredScrollDelta(
                    currentScroll =
                        state.listState.firstVisibleItemScrollOffset
                            .toFloat(),
                    centroid = centroid.y,
                    zoomChange = appliedZoom,
                    panChange = 0f,
                ),
            )
        }
    }

    Box(
        modifier =
            modifier
                .onSizeChanged { viewportSizePixels = it }
                .pdfTransformGestures(
                    enabled = gestureZoomEnabled,
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
        if (
            viewportSizePixels.width == 0 ||
            viewportSizePixels.height == 0
        ) {
            Box(Modifier.matchParentSize())
            return@Box
        }

        val density = LocalDensity.current
        val pagePaddingPixels = with(density) { pagePadding.roundToPx() }
        val contentWidthPixels =
            (viewportSizePixels.width * state.zoom)
                .roundToInt()
                .coerceAtLeast(1)
        val displayedPageWidthPixels =
            (contentWidthPixels - pagePaddingPixels * 2)
                .coerceAtLeast(1)
        val contentWidth = with(density) { contentWidthPixels.toDp() }
        val pageWidth = with(density) { displayedPageWidthPixels.toDp() }
        val layoutMetrics =
            remember(
                viewportSizePixels,
                displayedPageWidthPixels,
                pagePaddingPixels,
            ) {
                PdfViewLayoutMetrics(
                    viewportWidth = viewportSizePixels.width,
                    viewportHeight = viewportSizePixels.height,
                    displayedPageWidth = displayedPageWidthPixels,
                    pagePadding = pagePaddingPixels,
                )
            }
        SideEffect {
            state.updateLayoutMetrics(document, layoutMetrics)
        }
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
                delay(RENDER_SETTLE_DELAY_MILLIS.milliseconds)
                settledRenderWidthPixels = requestedRenderWidthPixels
            }
        }
        val pageWidthPixels =
            settledRenderWidthPixels.takeIf { it > 0 }
                ?: requestedRenderWidthPixels

        Box(
            modifier =
                Modifier
                    .matchParentSize()
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
                    val pageSearchResults =
                        state.searchResultsFor(document, pageIndex)
                    PdfPage(
                        document = document,
                        pageIndex = pageIndex,
                        width = pageWidth,
                        renderWidth = pageWidthPixels,
                        state = state,
                        pageColor = pageColor,
                        pageBorder = pageBorder,
                        pageLoadingContent = pageLoadingContent,
                        pageErrorContent = pageErrorContent,
                        maxRenderDimension = maxRenderDimension,
                        onError = onPageError,
                        onLinkActivated = { link ->
                            activateLink(pageIndex, link)
                        },
                        searchResults = pageSearchResults,
                        selectedSearchResult =
                            state.selectedSearchResult,
                        searchHighlightStyle =
                            searchHighlightStyle,
                    )
                }
            }
        }
    }
}

private fun Modifier.pdfTransformGestures(
    enabled: Boolean,
    gestureKey: Any?,
    onTransformStarted: () -> Unit,
    onTransform: (centroid: Offset, zoomChange: Float) -> Unit,
    onTransformStopped: () -> Unit,
): Modifier =
    if (!enabled) {
        this
    } else {
        pointerInput(gestureKey, enabled) {
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
    pageBorder: BorderStroke?,
    pageLoadingContent:
        @Composable BoxScope.(pageIndex: Int) -> Unit,
    pageErrorContent:
        (@Composable BoxScope.(
            pageIndex: Int,
            error: Throwable,
        ) -> Unit)?,
    maxRenderDimension: Int,
    onError: (Int, Throwable) -> Unit,
    onLinkActivated: (PdfLink) -> Unit,
    searchResults: List<PdfViewSearchResult>,
    selectedSearchResult: PdfViewSearchResult?,
    searchHighlightStyle: PdfSearchHighlightStyle,
) {
    val cachedPageInformation =
        state.cachedPageInformation(document, pageIndex)
    val informationState by produceState<PdfPageInformationState>(
        initialValue =
            cachedPageInformation
                ?.let(PdfPageInformationState::Ready)
                ?: PdfPageInformationState.Loading,
        document,
        pageIndex,
        cachedPageInformation,
    ) {
        value = try {
            val information =
                cachedPageInformation
                    ?: document[pageIndex].information()
            check(
                information.size.width > 0.0 &&
                        information.size.height > 0.0,
            ) {
                "PDF page ${pageIndex + 1} has an invalid size"
            }
            state.cachePageInformation(
                document = document,
                pageIndex = pageIndex,
                information = information,
            )
            PdfPageInformationState.Ready(information)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            onError(pageIndex, failure)
            PdfPageInformationState.Failed(failure)
        }
    }

    val pageInformation = when (val current = informationState) {
        PdfPageInformationState.Loading -> {
            PdfPagePlaceholder(
                width = width,
                aspectRatio = DEFAULT_PAGE_ASPECT_RATIO,
                pageColor = pageColor,
                pageBorder = pageBorder,
                pageIndex = pageIndex,
                pageLoadingContent = pageLoadingContent,
            )
            return
        }

        is PdfPageInformationState.Failed -> {
            PdfPageError(
                width = width,
                aspectRatio = DEFAULT_PAGE_ASPECT_RATIO,
                pageIndex = pageIndex,
                pageColor = pageColor,
                pageBorder = pageBorder,
                error = current.error,
                defaultMessage =
                    "Page ${pageIndex + 1} could not be loaded",
                pageErrorContent = pageErrorContent,
            )
            return
        }

        is PdfPageInformationState.Ready -> current.information
    }

    val links by produceState(
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

    val aspectRatio = pageInformation.size.width.toFloat() / pageInformation.size.height.toFloat()
    val renderSize = remember(pageInformation, renderWidth, maxRenderDimension) {
        calculateRenderSize(
            pageInformation = pageInformation,
            requestedWidth = renderWidth,
            maximumDimension = maxRenderDimension,
        )
    }

    val cacheKey = remember(pageIndex, renderSize) {
        PdfRenderCacheKey(
            pageIndex = pageIndex,
            width = renderSize.width,
            height = renderSize.height,
        )
    }

    var renderState by remember(document, pageIndex) {
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
                renderState = PdfPageRenderState.Failed(failure)
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
                .optionalBorder(pageBorder)
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
            PdfPageRenderState.Loading ->
                pageLoadingContent(pageIndex)
            is PdfPageRenderState.Ready -> {
                Image(
                    bitmap = current.image,
                    contentDescription = "PDF page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
                if (searchResults.isNotEmpty()) {
                    PdfSearchHighlights(
                        results = searchResults,
                        selectedResult = selectedSearchResult,
                        pageInformation = pageInformation,
                        style = searchHighlightStyle,
                    )
                }
            }
            is PdfPageRenderState.Failed ->
                if (pageErrorContent != null) {
                    pageErrorContent(pageIndex, current.error)
                } else {
                    DefaultPageErrorContent(
                        message =
                            "Page ${pageIndex + 1} could not be rendered",
                    )
                }
        }
    }
}

@Composable
private fun PdfSearchHighlights(
    results: List<PdfViewSearchResult>,
    selectedResult: PdfViewSearchResult?,
    pageInformation: PdfPageInfo,
    style: PdfSearchHighlightStyle,
) {
    Canvas(Modifier.fillMaxSize()) {
        results.forEach { result ->
            val decoration =
                if (result == selectedResult) {
                    style.selectedMatch
                } else {
                    style.match
                }
            val padding = decoration.padding.toPx()
            val strokeWidth = decoration.strokeWidth.toPx()
            val cornerRadius = decoration.cornerRadius.toPx()
            result.match.bounds.forEach boundsLoop@{ bounds ->
                val mapped =
                    pdfRectToDisplayedRect(
                        bounds = bounds,
                        displayedWidth = size.width,
                        displayedHeight = size.height,
                        pageInformation = pageInformation,
                    )
                val left = mapped.left - padding
                val top = mapped.top - padding
                val width = mapped.width + padding * 2f
                val height = mapped.height + padding * 2f
                if (width <= 0f || height <= 0f) {
                    return@boundsLoop
                }
                val topLeft = Offset(left, top)
                val highlightSize = Size(width, height)
                val radius = CornerRadius(cornerRadius, cornerRadius)
                drawRoundRect(
                    color = decoration.fillColor,
                    topLeft = topLeft,
                    size = highlightSize,
                    cornerRadius = radius,
                )
                if (
                    strokeWidth > 0f &&
                    decoration.strokeColor.alpha > 0f
                ) {
                    drawRoundRect(
                        color = decoration.strokeColor,
                        topLeft = topLeft,
                        size = highlightSize,
                        cornerRadius = radius,
                        style = Stroke(width = strokeWidth),
                    )
                }
            }
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

internal fun pdfRectToDisplayedRect(
    bounds: PdfRect,
    displayedWidth: Float,
    displayedHeight: Float,
    pageInformation: PdfPageInfo,
): Rect {
    require(displayedWidth > 0f) {
        "displayedWidth must be positive"
    }
    require(displayedHeight > 0f) {
        "displayedHeight must be positive"
    }
    require(
        pageInformation.size.width > 0.0 &&
            pageInformation.size.height > 0.0,
    ) {
        "page size must have positive width and height"
    }

    val points =
        listOf(
            PdfPoint(bounds.left, bounds.bottom),
            PdfPoint(bounds.left, bounds.top),
            PdfPoint(bounds.right, bounds.bottom),
            PdfPoint(bounds.right, bounds.top),
        ).map { point ->
            pdfPointToDisplayed(
                point = point,
                displayedWidth = displayedWidth,
                displayedHeight = displayedHeight,
                pageInformation = pageInformation,
            )
        }
    return Rect(
        left = points.minOf(Offset::x),
        top = points.minOf(Offset::y),
        right = points.maxOf(Offset::x),
        bottom = points.maxOf(Offset::y),
    )
}

private fun pdfPointToDisplayed(
    point: PdfPoint,
    displayedWidth: Float,
    displayedHeight: Float,
    pageInformation: PdfPageInfo,
): Offset {
    val displayedPageWidth = pageInformation.size.width
    val displayedPageHeight = pageInformation.size.height
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
    val displayedPoint =
        when (pageInformation.rotation) {
            PdfRotation.Degrees0 ->
                PdfPoint(
                    x = point.x,
                    y = nativeHeight - point.y,
                )
            PdfRotation.Degrees90 ->
                PdfPoint(
                    x = point.y,
                    y = point.x,
                )
            PdfRotation.Degrees180 ->
                PdfPoint(
                    x = nativeWidth - point.x,
                    y = point.y,
                )
            PdfRotation.Degrees270 ->
                PdfPoint(
                    x = nativeHeight - point.y,
                    y = nativeWidth - point.x,
                )
        }
    return Offset(
        x =
            (displayedPoint.x / displayedPageWidth * displayedWidth)
                .toFloat(),
        y =
            (displayedPoint.y / displayedPageHeight * displayedHeight)
                .toFloat(),
    )
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
    pageBorder: BorderStroke?,
    error: Throwable,
    defaultMessage: String,
    pageErrorContent:
        (@Composable BoxScope.(
            pageIndex: Int,
            error: Throwable,
        ) -> Unit)?,
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .aspectRatio(aspectRatio)
                .background(pageColor)
                .optionalBorder(pageBorder),
        contentAlignment = Alignment.Center,
    ) {
        if (pageErrorContent != null) {
            pageErrorContent(pageIndex, error)
        } else {
            DefaultPageErrorContent(defaultMessage)
        }
    }
}

@Composable
private fun PdfPagePlaceholder(
    width: Dp,
    aspectRatio: Float,
    pageColor: Color,
    pageBorder: BorderStroke?,
    pageIndex: Int,
    pageLoadingContent:
        @Composable BoxScope.(pageIndex: Int) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .width(width)
                .aspectRatio(aspectRatio)
                .background(pageColor)
                .optionalBorder(pageBorder),
        contentAlignment = Alignment.Center,
    ) {
        pageLoadingContent(pageIndex)
    }
}

@Composable
private fun DefaultPageErrorContent(message: String) {
    BasicText(
        text = message,
        style = TextStyle(color = Color(0xff991b1b)),
    )
}

private fun Modifier.optionalBorder(
    border: BorderStroke?,
): Modifier =
    if (border == null) {
        this
    } else {
        border(border)
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

    data class Failed(
        val error: Throwable,
    ) : PdfPageRenderState
}

private sealed interface PdfPageInformationState {
    data object Loading : PdfPageInformationState

    data class Ready(
        val information: PdfPageInfo,
    ) : PdfPageInformationState

    data class Failed(
        val error: Throwable,
    ) : PdfPageInformationState
}

private const val RENDER_WIDTH_QUANTUM: Int = 128
private const val RENDER_SETTLE_DELAY_MILLIS: Long = 100
private const val MIN_TRANSFORM_POINTERS: Int = 2
private const val DEFAULT_PAGE_ASPECT_RATIO: Float = 0.707f
private val DefaultOnUriLinkClick: (String) -> Unit = {}
