# JVM and Web PDFium Backends Design

## Goal

Complete the `pdf-core` PDFium backend for:

- JVM on macOS arm64, macOS x64, Linux x64, and Windows x64;
- Kotlin/JS in a browser;
- Kotlin/Wasm in a browser.

The implementation must consume the prebuilt PDFium 152.0.7961.0 artifacts
from `bblanchon/pdfium-binaries`. It must not require consumers to install a
C++ compiler or build PDFium.

## Scope

The first implementation targets capability parity with the currently
implemented Android and iOS backend:

- `PdfSource.Bytes`;
- password-protected documents and public PDF error mapping;
- process-wide PDFium initialization and document lifecycle;
- page count and document information;
- metadata and page labels;
- page size, intrinsic rotation, and bounding box;
- full-page BGRA8888 rendering;
- basic text extraction.

An operation that is not implemented must:

- have its corresponding `PdfCapabilities` value set to `false`, where a
  capability flag exists;
- throw `PdfUnsupportedFeatureException`;
- never return an empty value to represent missing implementation.

The following remain out of scope:

- `PdfSource.RandomAccess`;
- bookmarks, links, and thumbnails;
- text layout and search;
- progressive loading and rendering;
- forms and editing;
- PDF JavaScript and XFA;
- Node.js and WASI execution.

HTTP document loading remains out of scope. A web application may obtain PDF
bytes by any means, but it passes those bytes to `PdfSource.Bytes`.

## Version And Distribution

PDFium is pinned to:

```text
release: 152.0.7961.0
branch: chromium/7961
flavor: pdfium
V8: disabled
XFA: disabled
```

The existing `scripts/update-pdfium.sh` remains the only updater for upstream
artifacts. Network proxy values are supplied through the caller's environment
and are never stored in project files.

The updater must continue to stage all files before replacing checked-in
artifacts. It will also copy a runtime manifest beside JVM and Web resources,
so loaders can include the PDFium version and artifact SHA-256 in their cache
keys.

## Public API

The public `pdf-core` API remains unchanged. Consumers continue to call:

```kotlin
val document = PdfViewer.open(PdfSource.Bytes(bytes), password)
```

No JNA or JavaScript interop type may appear in a public declaration.

`PdfViewer.capabilities` reports the backend's implemented optional features.
JVM, JS, and Wasm will initially report the same capability baseline as
Android and iOS.

## Asynchronous Runtime Initialization

Emscripten initializes `pdfium.wasm` asynchronously. The current internal
`PdfiumBackend.initialize()` contract is synchronous and cannot safely model
that behavior.

The internal contract will change to:

```kotlin
internal interface PdfiumBackend {
    suspend fun initialize()
    fun destroy()
    // Existing operations remain synchronous.
}
```

The process-global call gate will accept a suspending operation. Consequently:

- `PdfiumRuntime.acquire()` becomes suspending;
- `PdfiumOperation.execute()` accepts a suspending lambda;
- Android, iOS, and JVM initialization completes immediately inside the
  suspending function;
- JS and Wasm await the Emscripten runtime promise while still preserving
  process-wide serialization;
- destruction and `PdfDocument.close()` remain synchronous.

Initialization failure must leave the runtime reference count at zero and
allow a later call to retry.

## JVM Backend

### Native Access

The JVM backend uses JNA 5.18.1 to call the upstream PDFium C API directly.
JNA is an `implementation` dependency of `jvmMain`; it is not exposed
transitively as a public API type. The library remains compiled for Java 11.

JDK native-access policy is outside the PDF API. Tests cover the supported
Java 11 baseline and a current JDK. Documentation must explain
`--enable-native-access=ALL-UNNAMED` for runtimes that require explicit native
access; the library must not mutate process JVM arguments.

The backend maps only the PDFium functions required by this scope. It does not
attempt to generate bindings for every exported symbol.

The low-level binding covers:

- library initialization and destruction;
- memory document open and document close;
- error retrieval and page count;
- document information, metadata, and page labels;
- page loading, page information, and page close;
- bitmap creation, fill, rendering, buffer access, and destruction;
- text page loading, text extraction, and text page close.

### Runtime Artifact Selection

The loader maps the JVM runtime to one checked-in resource classifier:

| Operating system | Architecture | Resource |
| --- | --- | --- |
| macOS | `aarch64` | `darwin-aarch64/libpdfium.dylib` |
| macOS | `x86_64` | `darwin-x86-64/libpdfium.dylib` |
| Linux | `x86_64` | `linux-x86-64/libpdfium.so` |
| Windows | `x86_64` | `win32-x86-64/pdfium.dll` |

Other combinations fail with `PdfUnsupportedFeatureException` before native
loading starts.

The selected resource is extracted beneath:

```text
${java.io.tmpdir}/pdf-viewer-kmp/pdfium/<version>/<classifier>/<sha256>/
```

Extraction uses:

- an inter-process file lock;
- a temporary file in the destination directory;
- SHA-256 verification before and after installation;
- an atomic move when supported;
- replacement of a corrupt cached file;
- an absolute path passed to JNA.

The loaded JNA library is retained for the process lifetime. PDFium's own
`FPDF_InitLibrary` and `FPDF_DestroyLibrary` lifecycle remains controlled by
`PdfiumRuntime`.

### Document And Memory Ownership

`FPDF_LoadMemDocument64` retains the source pointer for the document lifetime.
Each open JVM document therefore owns:

- JNA `Memory` containing an immutable copy of `PdfSource.Bytes`;
- the native `FPDF_DOCUMENT` pointer.

An internal handle registry maps `NativeDocumentHandle` values to that owned
state. Closing a document performs these steps inside the PDFium call gate:

1. remove the state from the registry;
2. call `FPDF_CloseDocument`;
3. release the JNA source memory reference.

No JNA pointer or memory object escapes the JVM backend.

### Rendering

Rendering allocates a native pixel buffer sized with checked arithmetic:

```text
width * height * 4
```

The backend creates an `FPDF_BITMAP` over that buffer, fills the requested
ARGB background, renders the page, and copies the resulting BGRA bytes into a
Kotlin-owned `ByteArray`.

Page, bitmap, and temporary native memory cleanup is unconditional. The
returned `PdfBitmap` owns only Kotlin memory and may outlive the document.

## Browser Backend

### Asset Layout

The browser artifact contains:

```text
pdfium/pdfium-adapter.js
pdfium/pdfium.js
pdfium/pdfium.wasm
pdfium/manifest.properties
```

`pdfium.js` and `pdfium.wasm` remain byte-for-byte upstream files.
`pdfium-adapter.js` is project-owned, readable source code.

### Adapter Responsibilities

The adapter is a classic browser script that installs one namespaced global
object. It must not expose or overwrite a generic global API after
initialization.

It is responsible for:

- deriving the PDFium asset directory from its own script URL;
- creating the Emscripten `Module` configuration before loading `pdfium.js`;
- resolving `pdfium.wasm` relative to the adapter;
- loading the upstream script only once;
- exposing a singleton initialization promise;
- converting Emscripten aborts and initialization failures into structured
  errors;
- allocating and freeing WebAssembly memory;
- retaining source allocations until document close;
- wrapping the small PDFium API subset required by this design;
- returning plain JavaScript numbers, strings, objects, and typed arrays.

The adapter owns a document registry. A document entry contains the PDFium
document pointer and the allocation holding its source bytes.

### Kotlin Interop

`webMain` contains one semantic `WebPdfiumBackend`. Platform-specific code is
limited to a small interop boundary:

```text
jsMain     -> Kotlin/JS external declarations
wasmJsMain -> Kotlin/Wasm JavaScript interop declarations
```

Both bindings call the same adapter methods and convert adapter values into
internal Kotlin values. PDF document semantics, validation, capability
reporting, and public exception mapping stay in `webMain`.

The JS Adapter returns copied render pixels. Kotlin then creates the same
Kotlin-owned `PdfBitmap` representation for both browser targets.

### Browser Resource Loading

The adapter URL is resolved relative to `document.baseURI`. Standard
Compose/Kotlin web distributions copy the `pdfium` resource directory beside
the application resources.

Loading failure includes the resolved URL in the internal diagnostic message.
It is surfaced as `PdfNativeException`, not as an untyped JavaScript error.

The design does not support loading PDF documents by URL and does not make the
PDFium runtime assets configurable through the common public API in this
iteration.

## Error Mapping

All three new backends use the existing public exception model:

| PDFium condition | Public exception |
| --- | --- |
| password missing | `PdfPasswordRequiredException` |
| password incorrect | `PdfIncorrectPasswordException` |
| invalid PDF format | `PdfInvalidFormatException` |
| unsupported security | `PdfUnsupportedSecurityException` |
| page load/render failure | `PdfPageException` |
| unsupported backend feature | `PdfUnsupportedFeatureException` |
| unexpected native/runtime failure | `PdfNativeException` |

Passwords containing a NUL character are rejected before entering native
code. Invalid dimensions and overflowing pixel sizes are rejected before
allocation.

Adapter and JNA diagnostics may be attached to exception messages, but public
exceptions must not expose JNA or JavaScript values.

## Concurrency And Closing

PDFium remains process-global and non-thread-safe. Every backend call is
serialized through the existing `PdfiumCallGate`.

The established lifecycle semantics remain unchanged:

- `PdfDocument.close()` is idempotent and synchronous;
- `closeAndAwait()` avoids blocking while waiting for an active operation;
- close invalidates every `PdfPage`;
- cancellation after rendering closes the produced bitmap;
- source ownership transfers to `PdfViewer.open()`;
- rendered pixels may outlive the document.

The browser executes on one JavaScript thread, but still uses the same gate so
reentrant close and lifecycle behavior match native platforms.

## Testing

### Shared Contract Tests

Existing common backend contracts remain authoritative. They will be extended
only where asynchronous initialization needs explicit coverage:

- concurrent first open initializes once;
- failed initialization does not increment the reference count;
- initialization can retry after failure;
- final close destroys once.

### JVM Tests

JVM tests cover:

- operating-system and architecture classifier mapping;
- cache path construction and digest verification;
- concurrent extraction;
- corrupt cache replacement;
- unsupported runtime rejection;
- real PDF open, information, metadata, page label, render, and text
  extraction using checked-in fixtures;
- password-required and incorrect-password behavior;
- repeated open/close and cancellation cleanup.

Tests that invoke PDFium run on each supported host in CI:

- macOS arm64;
- macOS x64;
- Linux x64;
- Windows x64.

### Browser Tests

Both `jsBrowserTest` and `wasmJsBrowserTest` run real PDFium tests for:

- adapter and Wasm initialization;
- concurrent initialization;
- document open and close;
- page information;
- deterministic pixel dimensions and buffer size;
- metadata, page labels, and text extraction;
- password errors;
- repeated open/close without retained adapter documents.

Adapter-level JavaScript tests verify source and temporary allocation counts
return to zero after success and failure.

## Update Verification

After `scripts/update-pdfium.sh chromium/7961`, verification must include:

```text
JVM host backend tests
Kotlin/JS browser backend tests
Kotlin/Wasm browser backend tests
Android native build and tests
iOS simulator backend tests
native core CTest
```

The updater itself does not claim an update is valid. The downloaded artifacts
are accepted only after the test matrix passes.

## Acceptance Criteria

The work is complete when:

- `PdfViewer.open(PdfSource.Bytes(...))` works on every supported JVM runtime,
  Kotlin/JS Browser, and Kotlin/Wasm Browser;
- the scoped information, rendering, and text APIs pass the same contract
  fixtures on all five platform families;
- unsupported APIs report unsupported status explicitly;
- no consumer-side native compilation is required;
- JVM and browser resources are loaded from the published library artifact;
- lifecycle, cancellation, and memory ownership tests pass;
- Android and iOS behavior remains unchanged.
