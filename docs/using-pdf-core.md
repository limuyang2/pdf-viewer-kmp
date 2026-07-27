# Using PDF Core

`pdf-core` is the UI-independent layer of PDF Viewer KMP. It opens documents,
reads document and page data, renders page bitmaps, extracts text, and exposes
PDF links.

## Dependency

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.limuyang2:pdf-core:0.1.1")
        }
    }
}
```

## Open a document

The currently implemented backends accept `PdfSource.Bytes`:

```kotlin
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfViewer

suspend fun inspectDocument(bytes: ByteArray) {
    val document = PdfViewer.open(PdfSource.Bytes(bytes))
    try {
        println("Pages: ${document.pageCount}")
        println("PDF version: ${document.information().version}")
        println("Title: ${document.metadata().title}")
    } finally {
        document.close()
    }
}
```

`PdfViewer.open()` takes ownership of the source immediately. A byte-backed
source retains the supplied `ByteArray` until the document closes, so the array
must not be mutated while the document is open.

`PdfSource.RandomAccess` is part of the API but is not implemented by the
current Android, iOS, JVM, or browser backends.

### Password-protected documents

```kotlin
val document =
    PdfViewer.open(
        source = PdfSource.Bytes(bytes),
        password = "secret",
    )
```

A missing password throws `PdfPasswordRequiredException`; an incorrect
password throws `PdfIncorrectPasswordException`.

## Resource ownership

- `PdfDocument` and `PdfBitmap` implement `AutoCloseable`; always close them.
- `close()` is idempotent.
- `PdfDocument.close()` waits synchronously for an active native operation.
- Use `document.closeAndAwait()` when waiting must not block the calling
  thread.
- `PdfPage` is a lightweight descriptor. It becomes invalid when its parent
  document closes.
- A rendered bitmap owns its Kotlin pixel buffer and can outlive the document.
- PDFium calls are serialized internally, so public suspend APIs may be called
  from different coroutines.

## Document API

```kotlin
val information = document.information()
val metadata = document.metadata()
val label = document.pageLabel(pageIndex = 0)
val firstPage = document[0]
```

`PdfDocumentInfo` contains the PDF version, permissions, security revision,
cross-reference validity, and linearization status. `PdfMetadata` exposes the
standard information dictionary fields without parsing PDF date strings.

Page indexes are zero-based and must be in `0 until document.pageCount`.

## Inspect a page

```kotlin
val page = document[0]
val information = page.information()

println(information.size)        // PDF points
println(information.rotation)    // Intrinsic page rotation
println(information.boundingBox)
```

PDF geometry uses PDF points and a bottom-left origin.

## Render a page

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

Current rendering produces full-page `Bgra8888` pixels. `stride` is the byte
distance between adjacent rows; do not assume rows are tightly packed.

`PdfRenderRequest.sourceRect` is reserved for cropped rendering and is not
implemented by the current backends.

## Extract text

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

`PdfTextRange` uses PDFium character indexes, not Kotlin string indexes.
`textLayout()` and character geometry are currently unavailable on the public
backends.

## Search text

Text search is currently implemented on Android only:

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

Calling `search()` on another backend throws
`PdfUnsupportedFeatureException`.

## Read links

```kotlin
import io.github.limuyang2.pdf.core.PdfLinkTarget

document[0].links().forEach { link ->
    when (val target = link.target) {
        is PdfLinkTarget.Internal ->
            println("Page ${target.destination.pageIndex}")
        is PdfLinkTarget.Uri ->
            println(target.uri)
        is PdfLinkTarget.RemoteDocument ->
            println(target.filePath)
        is PdfLinkTarget.Unsupported ->
            println("Native action ${target.nativeActionType}")
    }
}
```

Each link contains one or more `PdfQuad` bounds in PDF page coordinates.

## Capabilities and exceptions

Use `PdfViewer.capabilities` before invoking optional functionality:

```kotlin
val capabilities = PdfViewer.capabilities
println("text=${capabilities.text}")
println("search=${capabilities.search}")
println("links=${capabilities.links}")
```

Expected failures derive from `PdfException`, including:

- `PdfPasswordRequiredException`
- `PdfIncorrectPasswordException`
- `PdfInvalidFormatException`
- `PdfUnsupportedSecurityException`
- `PdfIoException`
- `PdfPageException`
- `PdfClosedException`
- `PdfUnsupportedFeatureException`
- `PdfNativeException`

Cancellation is propagated as coroutine cancellation rather than converted
into `PdfException`.

## Platform-specific setup

### Android

Android supports API 24 and newer. The PDFium JNI runtime is loaded
automatically; no manual initialization is required.

### JVM desktop

The JVM artifact bundles PDFium for:

- macOS arm64 and x64;
- Linux x64;
- Windows x64.

At first use, the matching library is verified and extracted below the system
temporary directory. Unsupported OS/architecture combinations throw
`PdfUnsupportedFeatureException`.

JVM runtimes that enforce native-access policy may require:

```text
--enable-native-access=ALL-UNNAMED
```

### Browser: JavaScript and Wasm

The web backend loads PDFium assets at runtime. The deployed site must serve:

```text
pdfium/manifest.properties
pdfium/pdfium-adapter.js
pdfium/pdfium.js
pdfium/pdfium.wasm
```

The source files are under `pdf-core/src/webMain/resources/pdfium`. The sample
application copies them with
`webApp/webpack.config.d/pdfium-assets.js`.

To use another directory, set the base URL before the first call to
`PdfViewer.open()`:

```javascript
globalThis.__pdfViewerPdfiumBaseUrl = "/assets/pdfium/";
```

The trailing slash is required.

### iOS

The bundled iOS binaries require iOS 26.0 or newer. Supported targets are
device arm64 and simulator arm64.

The Kotlin framework links PDFium dynamically as:

```text
@rpath/libpdfium.dylib
```

The application must copy and code-sign the matching library:

```text
pdf-core/src/nativeInterop/cinterop/lib/iosArm64/libpdfium.dylib
pdf-core/src/nativeInterop/cinterop/lib/iosSimulatorArm64/libpdfium.dylib
```

The sample `iosApp` Xcode project demonstrates selecting, embedding, and
signing the correct dylib.

## Current limitations

The following are not implemented yet:

- random-access input sources;
- cropped or tiled page rendering;
- embedded thumbnails;
- bookmarks;
- text layout and character geometry;
- search outside Android;
- forms and editing;
- progressive loading or rendering;
- PDF JavaScript and XFA.

[Back to README](../README.md)
