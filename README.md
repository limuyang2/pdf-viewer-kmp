# PDF Viewer KMP

PDF Viewer KMP provides PDFium-backed document access and a Compose
Multiplatform viewer for Kotlin Multiplatform applications.

The repository separates PDF content access from Compose presentation:

- `pdf-core` owns the PDF API, PDFium backends, and native integration.
- `pdf-viewer` is the Compose Multiplatform UI library and depends on
  `pdf-core`.

The current preview supports Android and iOS arm64. JVM, JavaScript, and Wasm
artifacts contain their PDFium binaries, but their Kotlin backends are still
planned.

## Installation

A Maven artifact is not published yet. When using this repository directly,
add the library module to `commonMain`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":pdf-core"))
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
| Android arm32/arm64/x86/x64 | Available |
| JVM | Planned |
| JavaScript/Wasm | Planned |

The bundled PDFium build is pinned to `chromium/7961`, uses binaries from
[`bblanchon/pdfium-binaries`](https://github.com/bblanchon/pdfium-binaries),
and does not include V8 or XFA.

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

Android and iOS both support:

- `PdfSource.Bytes` and optional passwords;
- page count and document lifecycle;
- page size, intrinsic rotation, and bounding box;
- full-page BGRA8888 rendering, including background color, annotation,
  grayscale, LCD text, and quarter-turn rotation options.

The iOS backend additionally supports:

- PDF version, permissions, metadata, and page labels;
- basic page text extraction.

Use `PdfViewer.capabilities` to inspect optional backend features at runtime.

## Current limitations

The following features are not available yet:

- random-access sources;
- cropped or region rendering with `sourceRect`;
- thumbnails;
- Android document metadata and text extraction;
- text layout, character geometry, and search;
- links and bookmarks;
- forms, editing, progressive loading/rendering, JavaScript, and XFA.

Calling an unavailable feature throws `PdfUnsupportedFeatureException`.
