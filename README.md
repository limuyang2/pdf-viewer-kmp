# PDF Viewer KMP

PDF Viewer KMP provides a platform-neutral Kotlin API for opening, inspecting,
rendering, and reading PDF documents with
[PDFium](https://pdfium.googlesource.com/pdfium/).

The current preview supports iOS arm64. Android, JVM, JavaScript, and Wasm
artifacts contain their PDFium binaries, but their Kotlin backends are still
planned.

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

## Platform support

| Platform | Status |
| --- | --- |
| iOS device arm64 | Available |
| iOS simulator arm64 | Available |
| iOS x64 | Not supported |
| iOS Catalyst | Not supported |
| Android | Planned |
| JVM | Planned |
| JavaScript/Wasm | Planned |

The bundled PDFium build is pinned to `chromium/7961`, uses binaries from
[`bblanchon/pdfium-binaries`](https://github.com/bblanchon/pdfium-binaries),
and does not include V8 or XFA.

## Open and inspect a document

```kotlin
import io.github.limuyang2.pdf.viewer.PdfSource
import io.github.limuyang2.pdf.viewer.PdfViewer

suspend fun inspectPdf(pdfBytes: ByteArray) {
    val document = PdfViewer.open(PdfSource.Bytes(pdfBytes))
    try {
        println("Pages: ${document.pageCount}")
        println("Title: ${document.metadata().title}")

        if (document.pageCount > 0) {
            val page = document[0]
            val information = page.information()
            println(
                "Page size: ${information.size.width} × " +
                    "${information.size.height} points",
            )
            println("Text: ${page.extractText()}")
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
import io.github.limuyang2.pdf.viewer.PdfPixelSize
import io.github.limuyang2.pdf.viewer.PdfRenderRequest
import io.github.limuyang2.pdf.viewer.PdfSource
import io.github.limuyang2.pdf.viewer.PdfViewer

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
- A `PdfPage` is a lightweight descriptor and becomes unusable when its parent
  document is closed.
- Do not mutate a `PdfSource.Bytes` array while its document is open.
- A rendered bitmap owns its Kotlin pixel buffer and may outlive the document.
- PDFium calls are serialized internally; callers may use the suspend API from
  different coroutines.

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
  `pdf-viewer/src/nativeInterop/cinterop/lib/iosArm64/libpdfium.dylib`
- simulator:
  `pdf-viewer/src/nativeInterop/cinterop/lib/iosSimulatorArm64/libpdfium.dylib`

The included `iosApp` Xcode project already selects, embeds, and signs the
correct dylib.

## Available API

The current iOS backend supports:

- `PdfSource.Bytes` and optional passwords;
- page count, PDF version, permissions, metadata, and page labels;
- page size, intrinsic rotation, and bounding box;
- full-page BGRA8888 rendering, including background color, annotation,
  grayscale, LCD text, and quarter-turn rotation options;
- basic page text extraction.

Use `PdfViewer.capabilities` to inspect optional backend features at runtime.

## Current limitations

The following features are not available yet:

- random-access sources;
- cropped or region rendering with `sourceRect`;
- thumbnails;
- text layout, character geometry, and search;
- links and bookmarks;
- forms, editing, progressive loading/rendering, JavaScript, and XFA.

Calling an unavailable feature throws `PdfUnsupportedFeatureException`.
