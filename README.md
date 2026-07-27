# PDF Viewer KMP

[简体中文](README.zh-CN.md)

## Documentation / 文档

| Library | English | 简体中文 |
| --- | --- | --- |
| `pdf-core` | [Using PDF Core](docs/using-pdf-core.md) | [PDF Core 使用指南](docs/using-pdf-core.zh-CN.md) |
| `pdf-viewer` | [Using PDF Viewer](docs/using-pdf-viewer.md) | [PDF Viewer 使用指南](docs/using-pdf-viewer.zh-CN.md) |

PDF Viewer KMP is a PDFium-backed Kotlin Multiplatform library for reading,
rendering, and displaying PDF documents.

The project is split into two public libraries:

| Library | Purpose |
| --- | --- |
| `pdf-core` | Opens PDF documents and exposes metadata, page rendering, text, search, and links without requiring Compose UI. |
| `pdf-viewer` | Provides the `PdfView` Compose Multiplatform component. It depends on `pdf-core` transitively. |

## Platform support

| Platform | Supported targets |
| --- | --- |
| Android | arm32, arm64, x86, x64 |
| iOS | device arm64, simulator arm64 |
| JVM desktop | macOS arm64/x64, Linux x64, Windows x64 |
| Browser | JavaScript, Wasm |

iOS x64 and Catalyst are not currently supported. The bundled PDFium build is
pinned to `chromium/7961` and does not include V8 or XFA.

## Add the dependency

Add Maven Central and choose the library needed by `commonMain`:

```kotlin
repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // PDF APIs only:
            implementation("io.github.limuyang2:pdf-core:0.1.0")

            // Or the Compose viewer. pdf-core is included transitively:
            implementation("io.github.limuyang2:pdf-viewer:0.1.0")
        }
    }
}
```

## Quick start: PDF Core

Use `pdf-core` when the application needs to inspect or render PDFs without the
built-in Compose UI:

```kotlin
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer

suspend fun renderFirstPage(pdfBytes: ByteArray): ByteArray {
    val document = PdfViewer.open(PdfSource.Bytes(pdfBytes))
    try {
        require(document.pageCount > 0)

        val bitmap =
            document[0].render(
                PdfRenderRequest(
                    outputSize = PdfPixelSize(1200, 1600),
                ),
            )
        try {
            return bitmap.copyPixels()
        } finally {
            bitmap.close()
        }
    } finally {
        document.close()
    }
}
```

See [Using PDF Core](docs/using-pdf-core.md) for passwords, metadata, text,
search, links, bitmap formats, resource ownership, exceptions, and
platform-specific setup.

## Quick start: PDF Viewer

`pdf-viewer` displays every page in a vertically scrolling Compose view:

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.limuyang2.pdf.core.PdfDocument
import io.github.limuyang2.pdf.viewer.PdfView
import io.github.limuyang2.pdf.viewer.rememberPdfViewState

@Composable
fun DocumentPreview(document: PdfDocument) {
    val state = rememberPdfViewState()

    PdfView(
        document = document,
        state = state,
        modifier = Modifier.fillMaxSize(),
        maxZoom = 4f,
        gestureZoomEnabled = true,
    )
}
```

The caller owns `PdfDocument` and must close it when the screen is disposed.
`PdfView` only closes its temporary rendered bitmaps.

See [Using PDF Viewer](docs/using-pdf-viewer.md) for opening a document in
Compose, state control, zoom configuration, render limits, custom loading and
error UI, and link handling.

## Platform setup at a glance

- **Android:** supports API 24 and newer. The PDFium JNI runtime is loaded
  automatically.
- **JVM:** native PDFium libraries are bundled and extracted automatically to
  the system temporary directory. Some runtimes may require
  `--enable-native-access=ALL-UNNAMED`.
- **Browser:** deploy `manifest.properties`, `pdfium-adapter.js`, `pdfium.js`,
  and `pdfium.wasm` under the configured PDFium asset directory.
- **iOS:** the minimum deployment target is iOS 26.0. The application must
  embed and sign the matching `libpdfium.dylib`.

The detailed setup commands are in
[Using PDF Core](docs/using-pdf-core.md#platform-specific-setup).

## Current capabilities

All current backends support:

- byte-array sources and password-protected documents;
- document information, permissions, metadata, and page labels;
- page size, intrinsic rotation, and bounding boxes;
- full-page BGRA8888 rendering;
- basic text extraction;
- internal destinations, URI actions, and link annotation bounds.

Android additionally supports text search. Check `PdfViewer.capabilities`
before calling optional APIs.

Not yet implemented: bookmarks, embedded thumbnails, text layout geometry on
the public backends, search outside Android, random-access sources, cropped
rendering, forms, editing, progressive loading/rendering, JavaScript, and XFA.
Unavailable operations throw `PdfUnsupportedFeatureException`.

## License

[MIT](LICENSE)
