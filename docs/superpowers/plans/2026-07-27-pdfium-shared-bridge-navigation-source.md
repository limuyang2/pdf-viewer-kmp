# PDFium Shared Bridge, Navigation, and Source Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move native PDFium behavior behind one reusable C ABI, preserve complete PDF destination semantics, and make document source ownership deterministic.

**Architecture:** A platform-neutral C++ core owns PDFium document handles and exposes fixed-width C structures plus caller-owned buffers. Android JNI and iOS cinterop become data-conversion adapters; JVM can compile the same core later. Common Kotlin continues to expose semantic immutable values and owns source closure through `PdfDocumentState`.

**Tech Stack:** PDFium chromium/7961, C++17, CMake, Android JNI, Kotlin Multiplatform, Kotlin/Native cinterop, Gradle.

## Global Constraints

- Keep PDFium calls process-serialized through the existing `PdfiumCallGate`.
- Do not expose PDFium pointers or C constants through public Kotlin APIs.
- Keep JS/Wasm builds compiling while their backend remains unavailable.
- HTTP and progressive loading remain out of scope.
- Write behavior tests before production changes.

---

### Task 1: Shared C ABI and host contract test

**Files:**
- Create: `pdf-core-native/include/pdfviewer_core.h`
- Create: `pdf-core-native/src/pdfviewer_core.cpp`
- Create: `pdf-core-native/tests/pdfviewer_core_test.cpp`
- Create: `pdf-core-native/CMakeLists.txt`

**Interfaces:**
- Produces: `pdfv_initialize()`, `pdfv_destroy()`, `pdfv_open_memory()`, `pdfv_close_document()`, `pdfv_get_page_info()`, `pdfv_render_page()`, metadata, page-label, and text extraction buffer APIs.
- Produces: opaque `pdfv_document_t`, `pdfv_status_t`, `pdfv_page_info_t`, `pdfv_document_info_t`, and `pdfv_render_request_t`.

- [x] Write a host C++ test that opens a minimal PDF, checks page count and page size, renders BGRA pixels, and closes the document.
- [x] Configure and run the host test against the bundled macOS arm64 PDFium library; verify the test fails because the shared ABI does not exist.
- [x] Implement opaque document ownership, fixed-width status codes, caller-provided output buffers, and exception containment.
- [x] Re-run the host contract test and verify it passes.

### Task 2: Android adapter migration

**Files:**
- Modify: `pdf-core-android-native/src/main/cpp/CMakeLists.txt`
- Replace: `pdf-core-android-native/src/main/cpp/pdfviewer_bridge.cpp`
- Modify: `pdf-core/src/androidMain/kotlin/io/github/limuyang2/pdf/viewer/internal/AndroidPdfiumNative.kt`
- Modify: `pdf-core/src/androidMain/kotlin/io/github/limuyang2/pdf/viewer/internal/AndroidPdfiumBackend.kt`
- Test: `pdf-core/src/androidDeviceTest/kotlin/io/github/limuyang2/pdf/viewer/internal/AndroidPdfiumBackendCoreTest.kt`

**Interfaces:**
- Consumes: the C ABI from Task 1.
- Produces: JNI-only argument/result conversion with no direct `FPDF_*` calls.

- [x] Extend Android device tests to cover document information, metadata, page labels, and text extraction now supplied by the shared core.
- [x] Verify the new assertions fail with unsupported-feature errors.
- [x] Replace direct PDFium JNI logic with calls to `pdfv_*`, preserving typed Kotlin errors.
- [x] Build all four Android ABIs and run available Android compilation/host tests.

### Task 3: iOS adapter migration

**Files:**
- Create: `pdf-core/src/nativeInterop/cinterop/pdfviewer_core.def`
- Modify: `pdf-core/build.gradle.kts`
- Replace: `pdf-core/src/iosMain/kotlin/io/github/limuyang2/pdf/viewer/internal/IosPdfiumBackend.kt`
- Delete: `pdf-core/src/iosMain/kotlin/io/github/limuyang2/pdf/viewer/internal/IosPdfDocument.kt`
- Modify: `pdf-core/src/iosTest/kotlin/io/github/limuyang2/pdf/viewer/internal/IosPdfiumBackendCoreTest.kt`

**Interfaces:**
- Consumes: the C ABI from Task 1.
- Produces: Kotlin/Native adapter using generated `pdfv_*` bindings only.

- [x] Add an iOS test assertion that the backend uses the same metadata/text behavior as Android.
- [x] Verify the bridge binding or link initially fails.
- [x] Add reproducible Gradle tasks that compile `libpdfviewer_core.a` for `iosArm64` and `iosSimulatorArm64`.
- [x] Point cinterop at `pdfviewer_core.h`, migrate the backend, and remove raw PDFium calls from Kotlin.
- [x] Compile both iOS targets and run the simulator test with the PDFium dylib available to dyld.

### Task 4: Complete destination model

**Files:**
- Modify: `pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/viewer/PdfNavigation.kt`
- Modify: `pdf-core/src/commonTest/kotlin/io/github/limuyang2/pdf/viewer/contract/NavigationContract.kt`
- Modify: `pdf-core/src/commonTest/kotlin/io/github/limuyang2/pdf/viewer/contract/FakePdfBackendContractTest.kt`
- Modify: `docs/pdfium-api-design.md`

**Interfaces:**
- Produces: `PdfDestination(pageIndex, view)` and sealed `PdfDestinationView`.
- Covers: Unknown, XYZ, Fit, FitH, FitV, FitR, FitB, FitBH, and FitBV.

- [x] Write common tests constructing and comparing every PDFium destination mode, including nullable XYZ/FitH/FitV parameters.
- [x] Verify compilation fails against the old `x/y/zoom` model.
- [x] Implement the sealed destination view hierarchy and migrate fixtures.
- [x] Run common, JVM, Android host, JS, and Wasm compilation/tests.

### Task 5: Source ownership

**Files:**
- Modify: `pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/viewer/PdfSource.kt`
- Modify: `pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/viewer/PdfViewer.kt`
- Modify: `pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/viewer/internal/PdfDocumentState.kt`
- Modify: `pdf-core/src/commonTest/kotlin/io/github/limuyang2/pdf/viewer/internal/PdfDocumentLifecycleTest.kt`
- Modify: `README.md`
- Modify: `docs/pdfium-api-design.md`

**Interfaces:**
- Produces: idempotent `PdfSource.close()`.
- Contract: `PdfViewer.open()` takes ownership immediately; failed open, cancelled open, and document close each close the source exactly once.

- [x] Add lifecycle tests for successful close, failed open, cancelled open, and repeated document close.
- [x] Verify tests fail because sources are currently only dereferenced.
- [x] Make `PdfSource` closeable, make byte sources no-op on close, and centralize exactly-once source cleanup.
- [x] Run all feasible platform tests and native builds, documenting any device-only verification gap.
