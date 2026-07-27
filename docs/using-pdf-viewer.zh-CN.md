# PDF Viewer 使用指南

`pdf-viewer` 是 Compose Multiplatform UI 层。它将已打开的
`PdfDocument` 显示为纵向延迟加载的页面列表，并在缩放级别变化后重新渲染
页面。

## 添加依赖

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.limuyang2:pdf-viewer:0.1.0")
        }
    }
}
```

`pdf-core` 是 `pdf-viewer` 的 API 依赖，会被自动引入。

[PDF Core 使用指南](using-pdf-core.zh-CN.md#各平台配置)中说明的平台 PDFium
配置同样适用于 `pdf-viewer`。

## 打开并显示文档

在 `PdfView` 外部打开文档，将它保存在 Compose 状态中，并在离开组合时关闭：

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.limuyang2.pdf.core.PdfDocument
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer
import io.github.limuyang2.pdf.viewer.PdfView
import io.github.limuyang2.pdf.viewer.rememberPdfViewState
import kotlinx.coroutines.CancellationException

@Composable
fun PdfScreen(
    pdfBytes: ByteArray,
    modifier: Modifier = Modifier,
) {
    var document by remember(pdfBytes) {
        mutableStateOf<PdfDocument?>(null)
    }
    var failure by remember(pdfBytes) {
        mutableStateOf<Throwable?>(null)
    }
    val viewState = rememberPdfViewState()

    LaunchedEffect(pdfBytes) {
        try {
            document = PdfViewer.open(PdfSource.Bytes(pdfBytes))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            failure = error
        }
    }

    val currentDocument = document
    DisposableEffect(currentDocument) {
        onDispose {
            currentDocument?.close()
        }
    }

    when {
        currentDocument != null ->
            PdfView(
                document = currentDocument,
                state = viewState,
                modifier = modifier.fillMaxSize(),
            )
        failure != null ->
            Text(checkNotNull(failure).message ?: "无法打开 PDF")
        else ->
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
    }
}
```

调用方拥有 `PdfDocument`，`PdfView` 永远不会替调用方关闭文档。页面渲染完成后，
`PdfView` 会负责关闭用于转换 Compose 图片的临时 `PdfBitmap`。

## 配置 PdfView

```kotlin
PdfView(
    document = document,
    state = state,
    modifier = Modifier.fillMaxSize(),
    pageSpacing = 12.dp,
    pagePadding = 8.dp,
    pageColor = Color.White,
    maxRenderDimension = 4096,
    maxZoom = 5f,
    gestureZoomEnabled = true,
)
```

主要参数：

| 参数 | 默认值 | 说明 |
| --- | --- | --- |
| `state` | `rememberPdfViewState()` | 保存滚动位置、缩放、当前页和渲染缓存。 |
| `pageSpacing` | `12.dp` | 相邻页面之间的纵向间距。 |
| `pagePadding` | `0.dp` | 页面列表周围的内边距。 |
| `pageColor` | `Color.White` | 每个已渲染页面背后的背景颜色。 |
| `pageBorder` | 1 dp 半透明边框 | 传入 `null` 可移除页面边框。 |
| `maxZoom` | `4f` | 页面最大显示缩放倍数，必须是有限且不小于 `1f` 的数值。 |
| `gestureZoomEnabled` | `true` | 是否启用多指缩放；关闭后仍可以通过状态进行程序化缩放。 |
| `maxRenderDimension` | `4096` | 每个渲染位图的最大宽度或高度，单位为像素。 |

`maxZoom` 和 `maxRenderDimension` 解决的是两个不同问题：

- `maxZoom` 限制页面在屏幕上最多可以放大多少倍。
- `maxRenderDimension` 限制位图内存占用和页面渲染成本。

页面显示尺寸超过 `maxRenderDimension` 后，仍然可以按照要求的倍数显示，但由于
位图分辨率已经到达上限，继续放大可能会逐渐变模糊。

## 手势行为

- 单指可以纵向或横向滚动文档。
- 双指手势用于改变缩放倍数。
- 双指缩放期间会忽略平移增量，避免与滚动容器竞争而产生抖动。
- `gestureZoomEnabled` 为 `false` 时，仍可使用
  `PdfViewState.updateZoom()` 和 `zoomBy()`。

## 控制 Viewer 状态

```kotlin
val state =
    rememberPdfViewState(
        initialPage = 0,
        initialZoom = 1f,
    )
```

可用的控制方法：

```kotlin
state.updateZoom(2f)
state.zoomBy(1.25f)
state.scrollToPage(pageIndex = 4)
state.animateScrollToPage(pageIndex = 4)
state.clearRenderCache()
```

`state.currentPage` 表示第一个可见页面的索引，索引从 0 开始。
`state.zoom`、延迟列表位置和横向滚动位置都支持状态保存。当前
`PdfView.maxZoom` 同样会限制程序化缩放。

需要从协程调用 suspend 滚动方法：

```kotlin
val scope = rememberCoroutineScope()

Button(
    onClick = {
        scope.launch {
            state.animateScrollToPage(10)
        }
    },
) {
    Text("第 11 页")
}
```

## 加载状态和页面错误

读取页面信息或渲染图片期间，会显示 `pageLoadingContent`。
`pageErrorContent` 可以替换内置的页面错误信息：

```kotlin
PdfView(
    document = document,
    pageLoadingContent = { pageIndex ->
        CircularProgressIndicator()
    },
    pageErrorContent = { pageIndex, error ->
        Text("第 ${pageIndex + 1} 页：${error.message}")
    },
    onPageError = { pageIndex, error ->
        println("PDF 第 $pageIndex 页加载失败：$error")
    },
)
```

`onPageError` 用于记录或上报错误，`pageErrorContent` 用于控制错误 UI。
文档打开错误发生在创建 `PdfView` 之前，必须由调用方处理。

## 链接处理

默认链接行为如下：

- 内部目标会滚动到目标页面；
- URI 链接通过 Compose `LocalUriHandler` 打开；
- 远程文档链接和不支持的动作会被忽略。

`onLinkClick` 会在默认行为之前执行。返回 `true` 表示已经消费该链接：

```kotlin
PdfView(
    document = document,
    onLinkClick = { link ->
        println("PDF 链接：$link")
        false
    },
    onUriLinkClick = { uri ->
        println("由应用打开 URI：$uri")
    },
    onLinkError = { pageIndex, link, error ->
        println("第 $pageIndex 页链接处理失败：$link，$error")
    },
)
```

传入 `onUriLinkClick = null` 可以忽略 URI 链接。

## 渲染与缓存

缩放为 `1f` 时，`PdfView` 会让页面宽度适配可用视口。渲染宽度会被量化到
稳定的尺寸区间，因此微小尺寸变化不会立即触发新的页面渲染。

双指缩放期间会先缩放当前位图；手势停止后，再渲染更清晰的新位图。

状态对象会维护一个小型内存页面缓存。绑定其他文档或调用
`clearRenderCache()` 时，缓存会被清空。

## Viewer 当前限制

当前 Viewer 尚未提供：

- 可选择文本；
- 搜索 UI 和搜索结果高亮；
- 书签或缩略图导航；
- 表单交互；
- 极高缩放级别所需的分块渲染。

这些是 Viewer UI 层的限制，与 `pdf-core` 提供的底层能力相互独立。

[返回 README](../README.md) · [English](using-pdf-viewer.md)
