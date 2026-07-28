# PDF Core 使用指南

`pdf-core` 是 PDF Viewer KMP 中不依赖 UI 的底层库，可用于打开 PDF
文档、读取文档和页面信息、渲染页面位图、提取文本以及读取 PDF 链接。

## 添加依赖

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.limuyang2:pdf-core:0.2.2")
        }
    }
}
```

## 打开文档

当前已实现的后端支持 `PdfSource.Bytes`：

```kotlin
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer

suspend fun inspectDocument(bytes: ByteArray) {
    val document = PdfViewer.open(PdfSource.Bytes(bytes))
    try {
        println("页数：${document.pageCount}")
        println("PDF 版本：${document.information().version}")
        println("标题：${document.metadata().title}")
    } finally {
        document.close()
    }
}
```

调用 `PdfViewer.open()` 后，输入源的所有权会立即转移给库。使用字节数组作为
输入源时，文档关闭前会一直持有该 `ByteArray`，因此文档打开期间不能修改数组。

`PdfSource.RandomAccess` 已包含在公开 API 中，但当前 Android、iOS、JVM
和浏览器后端尚未实现该功能。

### 密码保护的文档

```kotlin
val document =
    PdfViewer.open(
        source = PdfSource.Bytes(bytes),
        password = "secret",
    )
```

未提供密码时会抛出 `PdfPasswordRequiredException`，密码错误时会抛出
`PdfIncorrectPasswordException`。

## 资源所有权

- `PdfDocument` 和 `PdfBitmap` 均实现了 `AutoCloseable`，使用完毕后必须关闭。
- `close()` 可以安全地重复调用。
- `PdfDocument.close()` 会同步等待正在执行的原生操作结束。
- 如果不能阻塞调用线程，可以使用 `document.closeAndAwait()`。
- `PdfPage` 只是轻量级页面描述对象；其所属文档关闭后，该页面对象即失效。
- 已渲染的位图拥有独立的 Kotlin 像素缓冲区，可以比文档存活更长时间。
- PDFium 调用会在内部串行执行，公开的 suspend API 可以从不同协程调用。

## 文档信息

```kotlin
val information = document.information()
val metadata = document.metadata()
val label = document.pageLabel(pageIndex = 0)
val firstPage = document[0]
```

`PdfDocumentInfo` 包含 PDF 版本、权限、安全处理器版本、交叉引用表有效性和
线性化状态。`PdfMetadata` 提供标准文档信息字典字段；PDF 日期字符串不会被
自动解析。

页面索引从 0 开始，必须位于 `0 until document.pageCount` 范围内。

## 获取页面信息

```kotlin
val page = document[0]
val information = page.information()

println(information.size)        // PDF point
println(information.rotation)    // 页面固有旋转方向
println(information.boundingBox)
```

PDF 几何尺寸使用 PDF point，坐标原点位于页面左下角。

## 渲染页面

```kotlin
import io.github.limuyang2.pdf.core.PdfColor
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfRotation

val bitmap =
    document[0].render(
        PdfRenderRequest(
            outputSize = PdfPixelSize(width = 1200, height = 1600),
            rotation = PdfRotation.Degrees0,
            backgroundColor = PdfColor.White,
            renderAnnotations = true,
            grayscale = false,
            optimizeTextForLcd = false,
        ),
    )

try {
    val pixels = bitmap.copyPixels()
    println("${bitmap.width} × ${bitmap.height}")
    println("stride=${bitmap.stride}, format=${bitmap.format}")
} finally {
    bitmap.close()
}
```

当前渲染结果为完整页面的 `Bgra8888` 像素。`stride` 表示相邻两行之间的
字节距离，不能假设每行像素一定紧密排列。

`PdfRenderRequest.sourceRect` 为局部裁剪渲染预留，当前后端尚未实现。

## 提取文本

```kotlin
import io.github.limuyang2.pdf.core.PdfTextRange

val page = document[0]
val allText = page.extractText()
val firstCharacters =
    page.extractText(
        PdfTextRange(
            startCharacterIndex = 0,
            characterCount = 20,
        ),
    )
```

`PdfTextRange` 使用 PDFium 字符索引，而不是 Kotlin 字符串索引。
当前公开后端尚不支持 `textLayout()` 和字符几何信息。

## 搜索文本

Android、JVM、iOS、JS 和 WasmJS 后端均支持文本搜索：

```kotlin
import io.github.limuyang2.pdf.core.PdfSearchOptions

if (PdfViewer.capabilities.search) {
    val matches =
        document[0].search(
            query = "Compose",
            options =
                PdfSearchOptions(
                    matchCase = false,
                    matchWholeWord = true,
                ),
        )

    matches.forEach { match ->
        println("${match.range}: ${match.bounds}")
    }
}
```

## 读取链接

```kotlin
import io.github.limuyang2.pdf.core.PdfLinkTarget

document[0].links().forEach { link ->
    when (val target = link.target) {
        is PdfLinkTarget.Internal ->
            println("跳转到第 ${target.destination.pageIndex} 页")
        is PdfLinkTarget.Uri ->
            println(target.uri)
        is PdfLinkTarget.RemoteDocument ->
            println(target.filePath)
        is PdfLinkTarget.Unsupported ->
            println("不支持的原生动作：${target.nativeActionType}")
    }
}
```

每个链接包含一个或多个 `PdfQuad`，用于描述链接在 PDF 页面坐标系中的区域。

## 能力检测与异常

调用可选功能前，可通过 `PdfViewer.capabilities` 检查当前后端能力：

```kotlin
val capabilities = PdfViewer.capabilities
println("text=${capabilities.text}")
println("search=${capabilities.search}")
println("links=${capabilities.links}")
```

预期内的 PDF 错误都继承自 `PdfException`，包括：

- `PdfPasswordRequiredException`
- `PdfIncorrectPasswordException`
- `PdfInvalidFormatException`
- `PdfUnsupportedSecurityException`
- `PdfIoException`
- `PdfPageException`
- `PdfClosedException`
- `PdfUnsupportedFeatureException`
- `PdfNativeException`

协程取消会继续作为协程取消传播，不会转换成 `PdfException`。

## 各平台配置

### Android

Android 支持 API 24 及更高版本。PDFium JNI runtime 会自动加载，
不需要手动初始化。

### JVM 桌面端

JVM 版本内置以下平台的 PDFium：

- macOS arm64 和 x64；
- Linux x64；
- Windows x64。

首次使用时，库会校验对应的原生文件，并将它解压到系统临时目录。
不支持的操作系统或 CPU 架构会抛出 `PdfUnsupportedFeatureException`。

执行原生访问限制的 JVM 运行时可能需要：

```text
--enable-native-access=ALL-UNNAMED
```

### 浏览器：JavaScript 和 Wasm

Web 后端在运行时加载 PDFium 静态资源，部署后的网站必须提供：

```text
pdfium/manifest.properties
pdfium/pdfium-adapter.js
pdfium/pdfium.js
pdfium/pdfium.wasm
```

源码位于 `pdf-core/src/webMain/resources/pdfium`。示例应用通过
`webApp/webpack.config.d/pdfium-assets.js` 复制这些文件。

如果需要使用其他目录，必须在首次调用 `PdfViewer.open()` 之前设置基础 URL：

```javascript
globalThis.__pdfViewerPdfiumBaseUrl = "/assets/pdfium/";
```

路径末尾必须保留 `/`。

### iOS

内置 iOS 二进制要求 iOS 14.0 或更高版本。目前支持真机 arm64 和模拟器
arm64。

PDFium 和原生 bridge 已作为静态归档嵌入发布的 cinterop KLIB。外部 Kotlin
Multiplatform 工程只需声明 Maven 依赖，无需额外配置链接搜索路径、复制
dylib 或在运行时为 PDFium 签名。

## 当前限制

以下功能尚未实现：

- 随机访问输入源；
- 页面裁剪或分块渲染；
- 内嵌缩略图；
- 书签；
- 文本布局和字符几何信息；
- 表单和编辑；
- 渐进式加载或渲染；
- PDF JavaScript 和 XFA。

[返回 README](../README.md) · [English](using-pdf-core.md)
