# PDF Viewer KMP

PDF Viewer KMP provides PDFium-backed document access and a Compose
Multiplatform viewer for Kotlin Multiplatform applications.

The repository separates PDF content access from Compose presentation:

- `pdf-core` owns the PDF API, PDFium backends, and native integration.
- `pdf-viewer` is the Compose Multiplatform UI library and depends on
  `pdf-core`.

The current preview supports Android, iOS arm64, JVM desktop, JavaScript
browser, and Wasm browser targets.

## Installation

A Maven artifact is not published yet. When using this repository directly,
add the library module to `commonMain`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":pdf-viewer"))
        }
    }
}
```

Applications that only need document access and rendering pixels can depend on
`pdf-core` directly.

## Compose PDF preview

Open a document with `pdf-core`, then pass it to `PdfView`:

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
    )
}
```

`PdfView` displays a vertically scrolling page list, fits pages to the full
viewport width at 100%, and supports 1x to 4x zoom through gestures or
`PdfViewState`. Pass `pagePadding` when the viewer should retain space around
each page. It keeps a small rendered-page cache and limits either bitmap
dimension to 4096 pixels by default. Use `scrollToPage()`,
`animateScrollToPage()`, `updateZoom()`, and `clearRenderCache()` for external
controls.

PDF link annotations are clickable. Internal destinations jump to their target
page and URI actions open through the platform URI handler. Use `onLinkClick`
to inspect or replace the default behavior; return `true` when the callback
consumes the link.

The caller owns the `PdfDocument` and must close it when the preview leaves the
composition. `PdfView` closes its temporary `PdfBitmap` values but does not
close the document.

## Platform support

| Platform | Status |
| --- | --- |
| iOS device arm64 | Available |
| iOS simulator arm64 | Available |
| iOS x64 | Not supported |
| iOS Catalyst | Not supported |
| Android arm32/arm64/x86/x64 | Available |
| JVM macOS arm64/x64 | Available |
| JVM Linux x64 | Available |
| JVM Windows x64 | Available |
| JavaScript browser | Available |
| Wasm browser | Available |

The bundled PDFium build is pinned to `chromium/7961`, uses binaries from
[`bblanchon/pdfium-binaries`](https://github.com/bblanchon/pdfium-binaries),
and does not include V8 or XFA.

JVM applications on runtimes that enforce native-access policy may need:

```text
--enable-native-access=ALL-UNNAMED
```

The browser backend loads `pdfium/pdfium-adapter.js`, `pdfium.js`, and
`pdfium.wasm` relative to the page. The included `webApp` Webpack
configuration emits these files automatically. A different deployment path
can be selected before the first PDF is opened:

```javascript
globalThis.__pdfViewerPdfiumBaseUrl = "/assets/pdfium/";
```

The configured path must contain the four files from
`pdf-core/src/webMain/resources/pdfium`.

## Open and inspect a document

```kotlin
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer

suspend fun inspectPdf(pdfBytes: ByteArray) {
    val document = PdfViewer.open(PdfSource.Bytes(pdfBytes))
    try {
        println("Pages: ${document.pageCount}")

        if (document.pageCount > 0) {
            val page = document[0]
            val information = page.information()
            println(
                "Page size: ${information.size.width} × " +
                    "${information.size.height} points",
            )
        }
    } finally {
        document.close()
    }
}
```

Password-protected documents can be opened by passing `password`:

```kotlin
val document =
    PdfViewer.open(
        source = PdfSource.Bytes(pdfBytes),
        password = "secret",
    )
try {
    // Read or render the document.
} finally {
    document.close()
}
```

Missing and incorrect passwords are reported as
`PdfPasswordRequiredException` and `PdfIncorrectPasswordException`.

## Render a page

```kotlin
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer

suspend fun renderFirstPage(pdfBytes: ByteArray): RenderedPage {
    val document = PdfViewer.open(PdfSource.Bytes(pdfBytes))
    try {
        require(document.pageCount > 0)

        val bitmap =
            document[0].render(
                PdfRenderRequest(
                    outputSize = PdfPixelSize(width = 1200, height = 1600),
                ),
            )
        try {
            return RenderedPage(
                width = bitmap.width,
                height = bitmap.height,
                stride = bitmap.stride,
                bgraPixels = bitmap.copyPixels(),
            )
        } finally {
            bitmap.close()
        }
    } finally {
        document.close()
    }
}

data class RenderedPage(
    val width: Int,
    val height: Int,
    val stride: Int,
    val bgraPixels: ByteArray,
)
```

Rendering currently produces full-page `Bgra8888` pixels. `stride` is the
number of bytes between adjacent rows and should be used instead of assuming
tightly packed rows.

## Resource ownership

- Always close `PdfDocument` and `PdfBitmap`; both support idempotent `close()`.
- `PdfDocument.close()` is synchronous and returns only after native resources
  and the owned source are released. Use `closeAndAwait()` when waiting for an
  active operation must not block the calling thread.
- A `PdfPage` is a lightweight descriptor and becomes unusable when its parent
  document is closed.
- Calling `PdfViewer.open()` transfers ownership of its `PdfSource`; the
  source is closed after failure, cancellation, or document closure.
- Do not mutate a `PdfSource.Bytes` array while its document is open.
- A rendered bitmap owns its Kotlin pixel buffer and may outlive the document.
- PDFium calls are serialized internally; callers may use the public suspend
  API from different coroutines. Backend calls do not suspend while holding
  the process-wide PDFium gate.

## iOS integration

The bundled iOS PDFium binaries require iOS 26.0. Applications and frameworks
using this library must use the same or a newer deployment target.

`PdfViewerKit.framework` links PDFium dynamically:

```text
@rpath/libpdfium.dylib
```

The framework does not contain the PDFium dylib. An application consuming the
framework must embed and sign the matching binary:

- device:
  `pdf-core/src/nativeInterop/cinterop/lib/iosArm64/libpdfium.dylib`
- simulator:
  `pdf-core/src/nativeInterop/cinterop/lib/iosSimulatorArm64/libpdfium.dylib`

The included `iosApp` Xcode project already selects, embeds, and signs the
correct dylib.

## Available API

All implemented backends support:

- `PdfSource.Bytes` and optional passwords;
- page count and document lifecycle;
- page size, intrinsic rotation, and bounding box;
- full-page BGRA8888 rendering, including background color, annotation,
  grayscale, LCD text, and quarter-turn rotation options;
- PDF version, permissions, metadata, and page labels;
- basic page text extraction;
- internal page links, URI actions, and link annotation bounds.

Use `PdfViewer.capabilities` to inspect optional backend features at runtime.

## Current limitations

The following features are not available yet:

- tiled rendering for very large zoom levels;
- selectable text and viewer search UI;
- random-access sources;
- cropped or region rendering with `sourceRect`;
- thumbnails;
- text layout, character geometry, and search;
- bookmarks;
- forms, editing, progressive loading/rendering, JavaScript, and XFA.

Calling an unavailable feature throws `PdfUnsupportedFeatureException`.
