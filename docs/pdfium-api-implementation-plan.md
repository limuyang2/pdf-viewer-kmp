# PDFium KMP API Implementation Plan

Design source: [`pdfium-api-design.md`](pdfium-api-design.md)
Scope: viewer-first
Implementation order: common contract, iOS vertical slice, Android/JVM native
bridge, Web, parity hardening.

This plan deliberately uses small, independently verifiable milestones. A
milestone is not complete until its listed tests pass.

## Milestone 0: Preserve the binary baseline

### Goal

Make the current PDFium binary integration a stable prerequisite for API work.

### Work

- Keep PDFium pinned to `chromium/7961`.
- Treat `/Users/mumu/idea/pdfium` at `origin/chromium/7961` as the local source
  reference for implementation behavior.
- Add a script-level check that the installed headers and binary version match.
- Retain the iOS `@rpath/libpdfium.dylib` preparation.
- Do not add iOS x64 or Catalyst targets.
- Record unsupported platform/architecture combinations in README.

### Verification

- Android AAR still contains the four configured Android ABIs.
- JVM JAR still contains the declared native libraries.
- iOS device and simulator-arm64 frameworks link.
- JS and Wasm KLIBs still contain `pdfium.js` and `pdfium.wasm`.

## Milestone 1: Public value types

### Goal

Introduce dependency-free public models without opening PDFium.

### Files

```text
pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/viewer/
  PdfCapabilities.kt
  PdfColor.kt
  PdfException.kt
  PdfGeometry.kt
  PdfMetadata.kt
  PdfNavigation.kt
  PdfPermissions.kt
  PdfRendering.kt
  PdfSource.kt
  PdfText.kt
```

### Work

- Implement geometry, rotation, pixel size, colors, metadata, permissions,
  destinations, links, bookmarks, text ranges, search options, and exceptions.
- Validate dimensions, page rectangles, text ranges, and search queries at the
  public boundary.
- Keep all public constructors and properties platform-neutral.
- Add KDoc for coordinate systems, ownership, and index semantics.

### Tests

```text
pdf-core/src/commonTest/kotlin/io/github/limuyang2/pdf/viewer/
  PdfGeometryTest.kt
  PdfRenderRequestTest.kt
  PdfSourceTest.kt
  PdfTextRangeTest.kt
```

### Exit criteria

- Common metadata compiles.
- Android, JVM, iOS arm64, iOS simulator arm64, JS, and Wasm compilations pass.
- No public declaration contains a native pointer or platform image type.

## Milestone 2: Backend contract and fake backend

### Goal

Build and test the public object lifecycle before integrating native code.

### Files

```text
pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/viewer/
  PdfViewer.kt
  PdfDocument.kt
  PdfPage.kt

pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/viewer/internal/
  NativeDocumentHandle.kt
  OpenedDocument.kt
  PdfiumBackend.kt
  PdfiumBackendProvider.kt
  PdfDocumentState.kt

pdf-core/src/commonTest/kotlin/io/github/limuyang2/pdf/viewer/internal/
  FakePdfiumBackend.kt
  PdfDocumentLifecycleTest.kt
  PdfPageApiTest.kt
```

### Work

- Implement `PdfViewer.open()`.
- Implement lightweight `PdfPage` descriptors.
- Cache `pageCount` from the successful open result.
- Validate page indexes synchronously.
- Make `PdfDocument.close()` idempotent.
- Reject every operation after close.
- Ensure a page descriptor observes its parent document's closed state.
- Add an internal backend injection seam available only to tests.

### Tests

- successful open;
- backend open failure propagation;
- page index validation;
- repeated close invokes backend close exactly once;
- operations after close;
- page descriptor after document close;
- source retained until close and released afterward.

### Exit criteria

- The complete public viewer API can be exercised against a fake backend.
- No native or platform source set is required by common tests.

## Milestone 3: Runtime, global serialization, and cancellation boundaries

### Goal

Guarantee PDFium's process-wide single-entry requirement.

### Dependencies

Add `kotlinx-coroutines-core` to `pdf-core` commonMain.

### Files

```text
pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/viewer/internal/
  PdfiumRuntime.kt
  PdfiumCallGate.kt
  PdfiumOperation.kt

pdf-core/src/<platform>Main/kotlin/.../internal/
  PlatformPdfiumCallGate.<platform>.kt
```

### Work

- Add process-global runtime reference counting.
- Initialize PDFium before the first document opens.
- Destroy PDFium only after the last document closes.
- Serialize complete semantic operations, not individual C calls.
- Check coroutine cancellation before native entry and after native completion.
- Coordinate close with an active operation without destroying a live handle.
- Ensure exceptions cannot skip child-handle cleanup or runtime release.

### Tests

- multiple documents share one runtime initialization;
- closing one document does not destroy a runtime used by another;
- last close destroys the runtime;
- failed open releases its runtime reference;
- 100 concurrent fake operations observe maximum native concurrency of one;
- close racing an operation waits and closes once;
- cancellation before native entry prevents backend invocation;
- cancellation after native completion discards the result safely.

### Exit criteria

- Global serialization is proven by common tests.
- Runtime reference counting has no negative or leaked count paths.

## Milestone 4: Reusable backend contract suite

### Goal

Define one behavior suite that every real backend must pass.

### Files

```text
pdf-core/src/commonTest/kotlin/io/github/limuyang2/pdf/viewer/contract/
  PdfBackendContract.kt
  DocumentContract.kt
  RenderingContract.kt
  TextContract.kt
  NavigationContract.kt
```

### Fixtures

Create small committed PDFs for:

- one blank page;
- rotated and differently sized pages;
- metadata and page labels;
- UTF-8/UTF-16 text including surrogate pairs;
- generated spaces and hyphens;
- searchable text;
- internal and URI links;
- nested bookmarks;
- encrypted user-password document;
- encrypted owner-password document;
- corrupt PDF;
- thumbnail-present and thumbnail-absent documents.

Fixtures must be deterministic and carry an explicit license/source note.

### Exit criteria

- The suite runs against the fake backend first.
- Platform test adapters only provide backend construction and fixture bytes.

## Milestone 5: iOS core vertical slice

### Goal

Prove the architecture end to end on the platform with working cinterop.

### Files

```text
pdf-core/src/iosMain/kotlin/io/github/limuyang2/pdf/viewer/internal/
  IosPdfiumBackend.kt
  IosPdfiumRuntime.kt
  IosPdfDocument.kt
  IosPdfBitmap.kt
  IosPdfError.kt
  IosPdfSource.kt
```

### First slice

- `FPDF_InitLibraryWithConfig`;
- runtime destruction;
- `PdfSource.Bytes`;
- `FPDF_LoadMemDocument64`;
- immediate `FPDF_GetLastError` mapping;
- page count;
- page information;
- full-page BGRA rendering;
- document and bitmap close.

### Memory rules

- Retain the input `ByteArray` for the full document lifetime.
- Pin input memory only for the required native lifetime.
- For rendering, allocate the Kotlin-owned pixel array first.
- Pin the pixel array while calling `FPDFBitmap_CreateEx`.
- Destroy `FPDF_BITMAP` before unpinning.
- Return a `PdfBitmap` that owns no PDFium handle.

### Follow-up slice

- random-access source through `FPDF_FILEACCESS` and `StableRef`;
- metadata, version, permissions, page label;
- thumbnail;
- text extraction;
- text layout;
- search;
- links;
- bookmarks.

### Tests

- Run the backend contract suite for `iosArm64` and
  `iosSimulatorArm64` where the host supports execution.
- Always compile and link both targets.
- Add a device/simulator smoke screen that opens a fixture and renders page 0.

### Exit criteria

- No `FPDF_PAGE`, `FPDF_TEXTPAGE`, `FPDF_SCHHANDLE`, or `FPDF_BITMAP` survives
  an operation boundary.
- Both iOS framework binaries depend on `@rpath/libpdfium.dylib`.
- The sample app embeds and signs the correct PDFium dylib.

## Milestone 6: Native bridge ABI

### Goal

Provide the narrow C/C++ layer used by Android and JVM.

### Files

```text
pdf-core/src/nativeInterop/bridge/
  CMakeLists.txt
  include/pdfviewer_bridge.h
  pdfviewer_bridge.cpp
  pdfviewer_document.cpp
  pdfviewer_render.cpp
  pdfviewer_text.cpp
  pdfviewer_navigation.cpp
  pdfviewer_jni.cpp
```

### Work

- Implement ABI version reporting.
- Implement runtime acquire/release reference counting.
- Implement opaque document handles.
- Catch every C++ exception before crossing the C/JNI boundary.
- Read and map `FPDF_GetLastError()` under the global lock.
- Scope page and child handles with RAII.
- Make whole JNI methods correspond to semantic operations.
- Use caller-provided buffers or paired allocation/free functions.
- Enforce resource limits before allocation.
- Prohibit callback re-entry.

### Native tests

```text
pdf-core/src/nativeInterop/bridge/tests/
  runtime_test.cpp
  document_test.cpp
  rendering_test.cpp
  text_test.cpp
  navigation_test.cpp
```

Use the same PDF fixtures as Kotlin contract tests.

### Exit criteria

- AddressSanitizer and UndefinedBehaviorSanitizer tests pass on a supported
  desktop host.
- Repeated open/render/close loops show no bridge-owned leaks.
- The bridge ABI exports only the intended symbols.

## Milestone 7: Android backend

### Goal

Connect the common backend to the native bridge through JNI.

### Files

```text
pdf-core/src/androidMain/kotlin/io/github/limuyang2/pdf/viewer/internal/
  AndroidPdfiumBackend.kt
  AndroidPdfiumNative.kt
  AndroidPdfBitmap.kt
  AndroidNativeLoader.kt

pdf-core/src/androidMain/cpp/
  CMakeLists.txt
```

### Work

- Build `libpdfviewer_bridge.so` for all supported Android ABIs.
- Link the bridge against the matching `libpdfium.so`.
- Load PDFium and the bridge in deterministic order.
- Register JNI methods in `JNI_OnLoad`.
- Use direct byte arrays only while pinned by JNI.
- Retain source ownership in the Kotlin document wrapper.
- Keep Android `Bitmap` conversion in an adapter.

### Tests

- Host tests for validation and lifecycle.
- Device/emulator contract tests for arm64 and x86_64 where available.
- APK inspection confirming both native libraries are packaged per ABI.
- Android 16 KB alignment inspection for all 64-bit shared libraries.

### Exit criteria

- Android passes the common backend contract suite.
- No JNI method exposes raw PDFium handles to public Kotlin code.

## Milestone 8: JVM native bridge packaging

### Goal

Support macOS arm64/x64, Linux x64, and Windows x64.

### Binary prerequisites

- macOS: bridge dylib linked using `@loader_path/libpdfium.dylib`;
- Linux: bridge `.so` linked with `$ORIGIN` rpath;
- Windows: bridge DLL linked with the PDFium import library;
- update the PDFium installer to retain `pdfium.dll.lib` for Windows bridge
  builds.

### Files

```text
pdf-core/src/jvmMain/kotlin/io/github/limuyang2/pdf/viewer/internal/
  JvmPdfiumBackend.kt
  JvmPdfiumNative.kt
  JvmNativeLoader.kt
  JvmPdfBitmap.kt

pdf-core/src/jvmMain/resources/pdfium/
  <platform>/libpdfviewer_bridge.*
```

### Loader behavior

1. Detect normalized OS and architecture.
2. Reject unsupported combinations with a typed unsupported error.
3. Extract PDFium and the bridge into one versioned temporary directory.
4. Verify bundled checksums before loading.
5. Load PDFium first and the bridge second.
6. Reuse an already extracted version safely across class loaders/processes.
7. Delete incomplete extraction directories after failure.

### Tests

- loader OS/architecture mapping;
- concurrent extraction;
- checksum mismatch;
- unsupported platform;
- complete backend contract suite on each CI host;
- packaged JAR inspection.

### Exit criteria

- macOS arm64/x64, Linux x64, and Windows x64 CI pass.
- Runtime loading does not depend on the working directory or global library
  path.

## Milestone 9: JS and Wasm loader

### Goal

Wrap the bundled Emscripten module with the same semantic backend.

### Files

```text
pdf-core/src/webMain/kotlin/io/github/limuyang2/pdf/viewer/internal/
  WebPdfiumModule.kt
  WebPdfiumMemory.kt
  WebPdfiumBackend.kt
  WebPdfBitmap.kt

pdf-core/src/jsMain/kotlin/.../internal/
  JsPdfiumInterop.kt

pdf-core/src/wasmJsMain/kotlin/.../internal/
  WasmPdfiumInterop.kt
```

### Work

- Load `pdfium.js` once.
- Resolve `pdfium.wasm` relative to the packaged resource.
- Await `onRuntimeInitialized` from suspending open.
- Allocate source bytes in the Emscripten heap and retain them until close.
- Free source and temporary output allocations deterministically.
- Mirror error mapping before another module call occurs.
- Implement random-access callbacks only after byte-source contract tests pass.
- Keep all module calls serialized even though browser execution is currently
  single-threaded.

### Tests

- module initializes once under concurrent opens;
- failed Wasm fetch;
- memory allocation/free accounting;
- complete byte-source backend contract suite in a browser;
- JS and Wasm packaged-resource inspection.

### Exit criteria

- JS and Wasm pass the same document, render, text, search, and navigation
  behavior suite.
- Repeated open/render/close does not monotonically grow tracked Wasm heap
  allocations.

## Milestone 10: Cross-platform parity and performance

### Goal

Finish the first viewer release with measured behavior.

### Work

- Compare render output dimensions, stride, pixel format, and golden hashes.
- Document allowed font-rendering tolerance.
- Benchmark open, page information, full render, tile render, text extraction,
  and search.
- Stress multiple documents and concurrent caller coroutines.
- Enforce bitmap byte-size and extracted-text limits.
- Verify no backend leaves child handles open after exceptions.
- Add API dump/binary compatibility validation for public Kotlin declarations.

### Exit criteria

- All supported platforms pass the backend contract suite.
- Public API dump matches the approved viewer-first surface.
- README contains minimal open/render/text examples.
- Unsupported features and platform combinations are explicit.

## Recommended implementation checkpoints

Code review should happen after each checkpoint:

1. public value types;
2. fake backend and lifecycle;
3. runtime serialization;
4. iOS open/page/render vertical slice;
5. complete iOS viewer backend;
6. native bridge ABI;
7. Android backend;
8. JVM packaging;
9. JS/Wasm backend;
10. parity and release hardening.

Do not start editing, forms, annotations, or progressive APIs before checkpoint
10 is complete. Those features depend on the resource and concurrency
guarantees established here.
