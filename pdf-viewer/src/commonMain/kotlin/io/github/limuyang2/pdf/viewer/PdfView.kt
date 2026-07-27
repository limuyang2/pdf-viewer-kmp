package io.github.limuyang2.pdf.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.limuyang2.pdf.core.PdfDocument
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRenderRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Displays every page in [document] as a vertically scrolling Compose view.
 *
 * The caller retains ownership of [document]. [PdfView] closes temporary
 * rendered bitmaps, but never closes the document itself.
 */
@Composable
public fun PdfView(
    document: PdfDocument,
    modifier: Modifier = Modifier,
    state: PdfViewState = rememberPdfViewState(),
    pageSpacing: Dp = 12.dp,
    pagePadding: Dp = 0.dp,
    backgroundColor: Color = Color(0xffe5e7eb),
    pageColor: Color = Color.White,
    maxRenderDimension: Int = 4096,
    onPageError: (pageIndex: Int, error: Throwable) -> Unit = { _, _ -> },
) {
    require(pageSpacing >= 0.dp) { "pageSpacing must be non-negative" }
    require(pagePadding >= 0.dp) { "pagePadding must be non-negative" }
    require(maxRenderDimension > 0) {
        "maxRenderDimension must be positive"
    }

    state.bind(document)
    val transformableState =
        rememberTransformableState { centroid, zoomChange, panChange, _ ->
            val previousZoom = state.zoom
            state.zoomBy(zoomChange)
            val appliedZoom = state.zoom / previousZoom
            state.horizontalScrollState.dispatchRawDelta(
                centroid.x * (appliedZoom - 1f) - panChange.x,
            )
            state.listState.dispatchRawDelta(
                centroid.y * (appliedZoom - 1f) - panChange.y,
            )
        }

    BoxWithConstraints(
        modifier =
            modifier
                .background(backgroundColor)
                .transformable(
                    state = transformableState,
                    canPan = { state.zoom > PdfViewState.MIN_ZOOM },
                ),
    ) {
        val viewportWidth = maxWidth
        val contentWidth = viewportWidth * state.zoom
        val pageWidth = (contentWidth - pagePadding * 2).coerceAtLeast(1.dp)
        val pageWidthPixels =
            with(LocalDensity.current) {
                quantizeRenderWidth(
                    width = pageWidth.roundToPx(),
                    maximum = maxRenderDimension,
                )
            }

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
                    )
                }
            }
        }
    }
}

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
        remember(document, cacheKey) {
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
        renderState = PdfPageRenderState.Loading
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
            renderState = PdfPageRenderState.Failed
            onError(pageIndex, failure)
        }
    }

    Box(
        modifier =
            Modifier
                .width(width)
                .aspectRatio(aspectRatio)
                .background(pageColor)
                .border(1.dp, Color(0x22000000)),
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
private const val DEFAULT_PAGE_ASPECT_RATIO: Float = 0.707f
