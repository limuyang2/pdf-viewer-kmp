# JVM and Web PDFium Backends Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `PdfViewer.open(PdfSource.Bytes(...))`, document inspection, page rendering, and basic text extraction work on supported JVM desktops, Kotlin/JS Browser, and Kotlin/Wasm Browser.

**Architecture:** JVM loads the checked-in PDFium dynamic library through JNA 5.18.1 and maps the required C API directly. JS and Wasm share a semantic backend in `webMain`; leaf source sets call a project-owned JavaScript adapter around a deterministically wrapped upstream Emscripten module. Process-global serialization and public lifecycle semantics remain in common code.

**Tech Stack:** Kotlin 2.4.10, Kotlin Multiplatform, kotlinx.coroutines 1.11.0, JNA 5.18.1, PDFium 152.0.7961.0, Kotlin/JS Browser, Kotlin/Wasm Browser, Gradle 9/AGP 9.3.1.

## Global Constraints

- JVM support is limited to macOS arm64/x64, Linux x64, and Windows x64.
- JS and Wasm support browsers only; Node.js and WASI are unsupported.
- Consume PDFium release `152.0.7961.0`, branch `chromium/7961`, flavor `pdfium`, with V8 and XFA disabled.
- Do not compile PDFium and do not require consumer-side native compilation.
- Keep the public `pdf-core` API free of JNA and JavaScript interop types.
- Support `PdfSource.Bytes`; HTTP loading and `PdfSource.RandomAccess` remain unsupported.
- Implement document information, metadata, page labels, page information, BGRA8888 rendering, and basic text extraction where reliable.
- Unimplemented operations report `capabilities=false` and throw `PdfUnsupportedFeatureException`; they never return fabricated empty values.
- Preserve synchronous idempotent `close()`, suspending `closeAndAwait()`, cancellation cleanup, and process-global PDFium serialization.
- Proxy values are caller environment only and are never committed.

---

## File Structure

### Common Runtime

- `pdf-core/src/commonMain/.../internal/PdfiumBackend.kt`: make initialization suspendable.
- `pdf-core/src/commonMain/.../internal/PdfiumCallGate.kt`: serialize suspending initialization.
- `pdf-core/src/commonMain/.../internal/PdfiumOperation.kt`: accept suspending operations.
- `pdf-core/src/commonMain/.../internal/PdfiumRuntime.kt`: reference-count asynchronous initialization.
- `pdf-core/src/commonMain/.../internal/OwnedPdfBitmap.kt`: shared Kotlin-owned bitmap implementation.

### JVM

- `pdf-core/src/jvmMain/.../internal/JvmPdfiumPlatform.kt`: OS/architecture classification.
- `pdf-core/src/jvmMain/.../internal/JvmPdfiumLibraryLoader.kt`: verified resource extraction and JNA loading.
- `pdf-core/src/jvmMain/.../internal/JvmPdfiumLibrary.kt`: narrow JNA PDFium declarations and structures.
- `pdf-core/src/jvmMain/.../internal/JvmPdfiumError.kt`: native error mapping.
- `pdf-core/src/jvmMain/.../internal/JvmPdfiumBackend.kt`: document registry and semantic backend.

### Browser

- `scripts/pdfium-web/pdfium-module-prefix.js`: namespaced Emscripten factory prefix.
- `scripts/pdfium-web/pdfium-module-suffix.js`: factory completion suffix.
- `scripts/pdfium-web/pdfium-adapter.js`: stable browser bridge and native allocation registry.
- `pdf-core/src/webMain/.../internal/WebPdfiumInterop.kt`: pure-Kotlin bridge contract.
- `pdf-core/src/webMain/.../internal/WebPdfiumBackend.kt`: shared JS/Wasm semantic backend.
- `pdf-core/src/jsMain/.../internal/JsPdfiumInterop.kt`: Kotlin/JS adapter calls.
- `pdf-core/src/wasmJsMain/.../internal/WasmPdfiumInterop.kt`: Kotlin/Wasm adapter calls.

### Packaging And Verification

- `scripts/update-pdfium.sh`: generate wrapped Web glue and runtime manifests.
- `gradle/libs.versions.toml`: pin JNA 5.18.1.
- `.github/workflows/pdf-core.yml`: supported-host and browser test matrix.
- `README.md`: update platform support and native-access guidance.

---

### Task 1: Make PDFium Initialization Suspendable

**Files:**
- Modify: `pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/core/internal/PdfiumBackend.kt`
- Modify: `pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/core/internal/PdfiumCallGate.kt`
- Modify: `pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/core/internal/PdfiumOperation.kt`
- Modify: `pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/core/internal/PdfiumRuntime.kt`
- Modify: `pdf-core/src/commonTest/kotlin/io/github/limuyang2/pdf/core/internal/FakePdfiumBackend.kt`
- Modify: `pdf-core/src/commonTest/kotlin/io/github/limuyang2/pdf/core/internal/PdfiumRuntimeTest.kt`
- Modify: platform call-gate actuals under `androidMain`, `iosMain`, `jvmMain`, and `webMain`

**Interfaces:**
- Produces: `suspend fun PdfiumBackend.initialize()`
- Produces: `suspend fun PdfiumRuntime.acquire(backend: PdfiumBackend)`
- Produces: `suspend fun <T> PdfiumCallGate.call(operation: suspend () -> T): T`
- Preserves: synchronous `PdfiumBackend.destroy()` and `PdfiumCallGate.close()`

- [ ] **Step 1: Add failing asynchronous initialization tests**

Add fake-backend controls:

```kotlin
var initializeFailure: Throwable? = null
var initializeBlock: (suspend () -> Unit)? = null

override suspend fun initialize() {
    initializeCount += 1
    initializeBlock?.invoke()
    initializeFailure?.let { throw it }
}
```

Add tests proving a failed initialization leaves
`PdfiumRuntime.referencesForTesting == 0`, permits a later retry, and two
concurrent first opens call `initialize()` once.

- [ ] **Step 2: Run the common JVM test and verify compilation/test failure**

Run:

```bash
./gradlew :pdf-core:jvmTest --tests '*PdfiumRuntimeTest'
```

Expected: FAIL because the backend and gate contracts are still synchronous.

- [ ] **Step 3: Convert the internal initialization path to suspend functions**

Use suspending lambdas through the gate:

```kotlin
internal expect suspend fun <T> platformPdfiumCall(
    operation: suspend () -> T,
): T
```

Keep the runtime state update after successful initialization:

```kotlin
suspend fun acquire(backend: PdfiumBackend) {
    val active = activeBackend
    check(active == null || active === backend)
    if (referenceCount == 0) {
        backend.initialize()
        activeBackend = backend
    }
    referenceCount += 1
}
```

- [ ] **Step 4: Run common and platform compilation checks**

Run:

```bash
./gradlew \
  :pdf-core:jvmTest \
  :pdf-core:compileAndroidMain \
  :pdf-core:compileKotlinIosArm64 \
  :pdf-core:compileKotlinJs \
  :pdf-core:compileKotlinWasmJs
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add pdf-core/src
git commit -m "refactor: support asynchronous PDFium initialization"
```

---

### Task 2: Share The Kotlin-Owned Bitmap Implementation

**Files:**
- Create: `pdf-core/src/commonMain/kotlin/io/github/limuyang2/pdf/core/internal/OwnedPdfBitmap.kt`
- Create: `pdf-core/src/commonTest/kotlin/io/github/limuyang2/pdf/core/internal/OwnedPdfBitmapTest.kt`
- Modify: `pdf-core/src/androidMain/kotlin/io/github/limuyang2/pdf/core/internal/AndroidPdfiumBackend.kt`
- Modify: `pdf-core/src/iosMain/kotlin/io/github/limuyang2/pdf/core/internal/IosPdfiumBackend.kt`
- Delete: `pdf-core/src/androidMain/kotlin/io/github/limuyang2/pdf/core/internal/AndroidPdfBitmap.kt`
- Delete: `pdf-core/src/iosMain/kotlin/io/github/limuyang2/pdf/core/internal/IosPdfBitmap.kt`

**Interfaces:**
- Produces: `internal class OwnedPdfBitmap(...) : PdfBitmap`
- Consumes: Kotlin-owned BGRA bytes from every platform backend

- [ ] **Step 1: Add failing common bitmap lifecycle tests**

Cover:

```kotlin
assertContentEquals(original, bitmap.copyPixels())
bitmap.copyPixels(destination, destinationOffset = 3)
bitmap.close()
bitmap.close()
assertFailsWith<PdfClosedException> { bitmap.copyPixels() }
```

Also test negative offsets and insufficient destination capacity.

- [ ] **Step 2: Run the focused test and verify failure**

```bash
./gradlew :pdf-core:jvmTest --tests '*OwnedPdfBitmapTest'
```

Expected: FAIL because `OwnedPdfBitmap` does not exist.

- [ ] **Step 3: Implement the shared bitmap**

Use the existing Android/iOS behavior exactly:

```kotlin
internal class OwnedPdfBitmap(
    override val width: Int,
    override val height: Int,
    override val stride: Int,
    pixels: ByteArray,
) : PdfBitmap {
    private var pixels: ByteArray? = pixels
    override val format = PdfPixelFormat.Bgra8888
    override val isClosed: Boolean get() = pixels == null
    override fun close() { pixels = null }
    // copy methods retain current validation and defensive copying.
}
```

Switch Android and iOS rendering to this class.

- [ ] **Step 4: Run bitmap and mobile backend tests**

```bash
./gradlew \
  :pdf-core:jvmTest \
  :pdf-core:testAndroidHostTest \
  :pdf-core:iosSimulatorArm64Test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add pdf-core/src
git commit -m "refactor: share owned PDF bitmap implementation"
```

---

### Task 3: Package Runtime Manifests And Namespaced Web Glue

**Files:**
- Create: `scripts/pdfium-web/pdfium-module-prefix.js`
- Create: `scripts/pdfium-web/pdfium-module-suffix.js`
- Create: `scripts/pdfium-web/pdfium-adapter.js`
- Modify: `scripts/update-pdfium.sh`
- Create after updater: `pdf-core/src/jvmMain/resources/pdfium/manifest.properties`
- Create after updater: `pdf-core/src/webMain/resources/pdfium/manifest.properties`
- Modify after updater: `pdf-core/src/webMain/resources/pdfium/pdfium.js`
- Create after updater: `pdf-core/src/webMain/resources/pdfium/pdfium-adapter.js`
- Create: `scripts/tests/update-pdfium-layout-test.sh`

**Interfaces:**
- Produces: `globalThis.__pdfViewerCreatePdfiumModule(config): Promise<Module>`
- Produces: `globalThis.__pdfViewerPdfium` adapter namespace
- Produces: runtime `manifest.properties` containing version and asset digests

- [ ] **Step 1: Add a failing shell layout test**

The test checks:

```sh
grep -q '__pdfViewerCreatePdfiumModule' \
  pdf-core/src/webMain/resources/pdfium/pdfium.js
test -f pdf-core/src/webMain/resources/pdfium/pdfium-adapter.js
cmp pdf-core/src/jvmMain/resources/pdfium/manifest.properties \
    pdf-core/src/webMain/resources/pdfium/manifest.properties
! grep -q 'globalThis.Module' \
  pdf-core/src/webMain/resources/pdfium/pdfium.js
```

It also checks the Wasm SHA-256 against the runtime manifest.

- [ ] **Step 2: Run the layout test and verify failure**

```bash
sh scripts/tests/update-pdfium-layout-test.sh
```

Expected: FAIL because the generated wrapper, adapter, and runtime manifests
are absent.

- [ ] **Step 3: Add deterministic wrapper templates**

The prefix establishes a local Emscripten `Module`:

```javascript
(function (root) {
  "use strict";
  root.__pdfViewerCreatePdfiumModule = function (configuration) {
    return new Promise(function (resolve, reject) {
      var Module = Object.assign({}, configuration, {
        onAbort: reject,
        onRuntimeInitialized: function () { resolve(Module); }
      });
```

The suffix closes the promise, factory, and IIFE. The complete upstream
`pdfium.js` source is inserted between these files without internal rewriting.

- [ ] **Step 4: Add the adapter loader skeleton**

The adapter:

- captures its own script directory from `document.currentScript.src`;
- loads wrapped `pdfium.js` once;
- calls `__pdfViewerCreatePdfiumModule`;
- passes `locateFile` returning the adjacent `pdfium.wasm`;
- exposes `initialize()`, `destroy()`, and allocation counters.

Use a namespaced global only:

```javascript
root.__pdfViewerPdfium = Object.freeze({
  initialize: initialize,
  destroy: destroy,
  debugAllocationCounts: debugAllocationCounts
});
```

- [ ] **Step 5: Update the updater and regenerate chromium/7961 resources**

Run with the approved proxy:

```bash
export https_proxy=http://127.0.0.1:7890
export http_proxy=http://127.0.0.1:7890
export all_proxy=socks5://127.0.0.1:7891
./scripts/update-pdfium.sh chromium/7961
```

The script concatenates prefix + upstream glue + suffix, copies the adapter,
and copies the runtime manifest to JVM and Web resource roots.

- [ ] **Step 6: Verify generated layout and existing native builds**

```bash
sh scripts/tests/update-pdfium-layout-test.sh
./gradlew \
  :pdf-core:compileKotlinJvm \
  :pdf-core:compileKotlinJs \
  :pdf-core:compileKotlinWasmJs \
  :pdf-core-android-native:assembleRelease
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add scripts pdf-core/src/jvmMain/resources pdf-core/src/webMain/resources
git commit -m "build: package JVM and browser PDFium runtimes"
```

---

### Task 4: Add Verified JVM Native Library Loading

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `pdf-core/build.gradle.kts`
- Create: `pdf-core/src/jvmMain/kotlin/io/github/limuyang2/pdf/core/internal/JvmPdfiumPlatform.kt`
- Create: `pdf-core/src/jvmMain/kotlin/io/github/limuyang2/pdf/core/internal/JvmPdfiumLibraryLoader.kt`
- Create: `pdf-core/src/jvmTest/kotlin/io/github/limuyang2/pdf/core/internal/JvmPdfiumPlatformTest.kt`
- Create: `pdf-core/src/jvmTest/kotlin/io/github/limuyang2/pdf/core/internal/JvmPdfiumLibraryLoaderTest.kt`

**Interfaces:**
- Produces: `JvmPdfiumPlatform.detect(osName, osArch): JvmPdfiumPlatform`
- Produces: `JvmPdfiumLibraryLoader.load(): JvmPdfiumLibrary`
- Consumes: `pdfium/manifest.properties` and classifier-specific native resource

- [ ] **Step 1: Pin JNA and add failing classifier tests**

Add:

```toml
jna = "5.18.1"
jna = { module = "net.java.dev.jna:jna", version.ref = "jna" }
```

Add `implementation(libs.jna)` only to `jvmMain`.

Test exact normalized inputs:

```text
Mac OS X + aarch64 -> darwin-aarch64/libpdfium.dylib
Mac OS X + x86_64  -> darwin-x86-64/libpdfium.dylib
Linux + amd64      -> linux-x86-64/libpdfium.so
Windows 11 + amd64 -> win32-x86-64/pdfium.dll
Linux + aarch64    -> PdfUnsupportedFeatureException
```

- [ ] **Step 2: Run focused tests and verify failure**

```bash
./gradlew :pdf-core:jvmTest --tests '*JvmPdfiumPlatformTest'
```

Expected: FAIL because platform detection is absent.

- [ ] **Step 3: Implement deterministic platform detection**

Use locale-independent lowercase normalization and explicit aliases only.
Include the unsupported `os.name` and `os.arch` in the exception message.

- [ ] **Step 4: Add failing extraction/cache tests**

Inject a resource reader, cache root, and digest function. Test:

- verified first extraction;
- existing valid cache reuse;
- corrupt cache replacement;
- two threads extracting the same artifact;
- absent resource;
- digest mismatch.

- [ ] **Step 5: Implement locked, verified extraction**

Use:

```text
<cache>/<version>/<classifier>/<sha256>/<native-file>
```

Acquire a `FileChannel.lock()` on a sibling lock file, write a uniquely named
temporary file, verify SHA-256, and use `ATOMIC_MOVE` with a normal move
fallback.

Load using the absolute extracted path:

```kotlin
Native.load(extracted.toString(), JvmPdfiumLibrary::class.java)
```

- [ ] **Step 6: Run JVM loader tests**

```bash
./gradlew :pdf-core:jvmTest \
  --tests '*JvmPdfiumPlatformTest' \
  --tests '*JvmPdfiumLibraryLoaderTest'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml pdf-core
git commit -m "feat: add verified JVM PDFium loading"
```

---

### Task 5: Implement JVM Open And Inspection

**Files:**
- Create: `pdf-core/src/jvmMain/kotlin/io/github/limuyang2/pdf/core/internal/JvmPdfiumLibrary.kt`
- Create: `pdf-core/src/jvmMain/kotlin/io/github/limuyang2/pdf/core/internal/JvmPdfiumError.kt`
- Create: `pdf-core/src/jvmMain/kotlin/io/github/limuyang2/pdf/core/internal/JvmPdfiumBackend.kt`
- Modify: `pdf-core/src/jvmMain/kotlin/io/github/limuyang2/pdf/core/internal/PlatformPdfiumCallGate.jvm.kt`
- Create: `pdf-core/src/jvmTest/kotlin/io/github/limuyang2/pdf/core/internal/JvmPdfiumBackendTest.kt`

**Interfaces:**
- Produces: `JvmPdfiumLibrary` narrow JNA C API mapping
- Produces: `JvmPdfiumBackend : PdfiumBackend`
- Changes provider: JVM returns `JvmPdfiumBackend`

- [ ] **Step 1: Add failing real-backend open tests**

Use decoded contract fixtures and verify:

```kotlin
val document = PdfViewer.open(PdfSource.Bytes(helloWorldBytes))
assertEquals(1, document.pageCount)
assertEquals(PdfSize(200.0, 200.0), document[0].information().size)
document.close()
```

Add encrypted fixture tests for missing, incorrect, and valid passwords.

- [ ] **Step 2: Run tests and verify unsupported-backend failure**

```bash
./gradlew :pdf-core:jvmTest --tests '*JvmPdfiumBackendTest'
```

Expected: FAIL with the current unavailable-backend exception.

- [ ] **Step 3: Define narrow JNA types and functions**

Define project-owned `SizeT`, pointer references, `FS_SIZEF`, and `FS_RECTF`.
Map C `unsigned long` with `NativeLong`, and `size_t` with `SizeT`.

The interface includes the exact C symbols required by this task:

```kotlin
internal interface JvmPdfiumLibrary : Library {
    fun FPDF_InitLibrary()
    fun FPDF_DestroyLibrary()
    fun FPDF_LoadMemDocument64(
        data: Pointer,
        size: SizeT,
        password: String?,
    ): Pointer?
    fun FPDF_GetLastError(): NativeLong
    fun FPDF_GetPageCount(document: Pointer): Int
    fun FPDF_CloseDocument(document: Pointer)
    fun FPDF_GetFileVersion(document: Pointer, version: IntByReference): Int
    fun FPDF_GetDocPermissions(document: Pointer): NativeLong
    fun FPDF_GetSecurityHandlerRevision(document: Pointer): Int
    fun FPDF_DocumentHasValidCrossReferenceTable(document: Pointer): Int
}
```

- [ ] **Step 4: Implement document ownership and errors**

Each registry entry owns both:

```kotlin
private data class JvmDocument(
    val sourceMemory: Memory,
    val document: Pointer,
)
```

Use a monotonically increasing Kotlin `Long` registry handle instead of
exposing native pointer values as public/internal document IDs.

Map PDFium error codes 1 through 6 to the existing public exceptions and use
whether a password was supplied to distinguish required from incorrect.

- [ ] **Step 5: Implement information, metadata, labels, and page information**

Metadata and labels use two-pass UTF-16LE buffer reads. Page operations always
close `FPDF_PAGE` in `finally`.

Unsupported backend functions call one helper:

```kotlin
private fun unsupported(feature: String): Nothing =
    throw PdfUnsupportedFeatureException(feature)
```

- [ ] **Step 6: Run JVM backend tests**

```bash
./gradlew :pdf-core:jvmTest --tests '*JvmPdfiumBackendTest'
```

Expected: PASS for open, passwords, information, metadata, labels, and page
information.

- [ ] **Step 7: Commit**

```bash
git add pdf-core/src/jvmMain pdf-core/src/jvmTest
git commit -m "feat: open and inspect PDFs on JVM"
```

---

### Task 6: Implement JVM Rendering And Text Extraction

**Files:**
- Modify: `pdf-core/src/jvmMain/kotlin/io/github/limuyang2/pdf/core/internal/JvmPdfiumLibrary.kt`
- Modify: `pdf-core/src/jvmMain/kotlin/io/github/limuyang2/pdf/core/internal/JvmPdfiumBackend.kt`
- Modify: `pdf-core/src/jvmTest/kotlin/io/github/limuyang2/pdf/core/internal/JvmPdfiumBackendTest.kt`
- Create: `pdf-core/src/jvmTest/kotlin/io/github/limuyang2/pdf/core/contract/JvmPdfBackendContractTest.kt`

**Interfaces:**
- Implements: `PdfPage.render(PdfRenderRequest): PdfBitmap`
- Implements: `PdfPage.extractText(PdfTextRange?): String`

- [ ] **Step 1: Add failing render and text tests**

Assert:

- width, height, stride, and BGRA format;
- `copyPixels().size == width * height * 4`;
- a white background fixture contains non-white pixels;
- grayscale output has equal B, G, and R channels for sampled opaque pixels;
- full and ranged extraction return expected fixture text;
- bitmap remains readable after document close.

- [ ] **Step 2: Run focused tests and verify unsupported failures**

```bash
./gradlew :pdf-core:jvmTest --tests '*JvmPdfiumBackendTest'
```

Expected: FAIL because render and text methods are not implemented.

- [ ] **Step 3: Add bitmap and text JNA functions**

Map:

```text
FPDF_LoadPage / FPDF_ClosePage
FPDFBitmap_CreateEx / FPDFBitmap_FillRect / FPDFBitmap_Destroy
FPDF_RenderPageBitmap
FPDFText_LoadPage / FPDFText_CountChars / FPDFText_GetText
FPDFText_ClosePage
```

Use checked `Long` arithmetic before converting pixel count to `Int`.

- [ ] **Step 4: Implement cleanup-safe rendering**

Allocate `Memory(pixelCount)`, create a BGRA bitmap over it, fill the
background, render, copy to `ByteArray`, and destroy page/bitmap in `finally`.
Return `OwnedPdfBitmap`.

- [ ] **Step 5: Implement UTF-16LE text extraction**

Normalize a nullable `PdfTextRange` to a validated start/count pair, call
`FPDFText_GetText`, and decode only the returned UTF-16 code units excluding
the trailing NUL.

- [ ] **Step 6: Run JVM contracts and all JVM tests**

```bash
./gradlew :pdf-core:jvmTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add pdf-core/src/jvmMain pdf-core/src/jvmTest
git commit -m "feat: render and extract PDF text on JVM"
```

---

### Task 7: Complete The Browser JavaScript Adapter

**Files:**
- Modify: `scripts/pdfium-web/pdfium-adapter.js`
- Regenerate: `pdf-core/src/webMain/resources/pdfium/pdfium-adapter.js`
- Create: `scripts/tests/pdfium-web-adapter-test.mjs`

**Interfaces:**
- Produces adapter methods:
  `initialize`, `destroy`, `open`, `close`, `documentInfo`, `metadata`,
  `pageLabel`, `pageInfo`, `render`, `extractText`,
  `debugAllocationCounts`

- [ ] **Step 1: Add a failing JavaScript adapter contract**

Load `pdfium-adapter.js` in a Node `vm` context containing a fake namespaced
PDFium module factory. The fake module implements the exact `_malloc`,
`_free`, document, page, bitmap, metadata, label, and text functions used by
the adapter.

Assert the adapter result shapes and ownership counters:

```javascript
assert.deepEqual(opened, {
  handle: 1,
  pageCount: 1,
  pdfiumError: 0
});
assert.equal(rendered.pixels.length, rendered.width * rendered.height * 4);
assert.deepEqual(adapter.debugAllocationCounts(), {
  documents: 1,
  nativeAllocations: 1
});
adapter.close(opened.handle);
assert.deepEqual(adapter.debugAllocationCounts(), {
  documents: 0,
  nativeAllocations: 0
});
```

- [ ] **Step 2: Run the adapter contract and verify failure**

```bash
node scripts/tests/pdfium-web-adapter-test.mjs
```

Expected: FAIL because the adapter has only initialization methods.

- [ ] **Step 3: Implement adapter document ownership**

For `open(bytes, password)`:

1. allocate source bytes with `_malloc`;
2. copy into `HEAPU8`;
3. allocate a UTF-8 password only when present;
4. call `_FPDF_LoadMemDocument64`;
5. retain source pointer in a `Map` keyed by an adapter-generated integer;
6. free password and temporary outputs;
7. on failure, free source and return the PDFium error code.

- [ ] **Step 4: Implement adapter inspection**

Use Emscripten heap views and explicit little-endian `DataView` access for
output structures. Metadata, page labels, and text use two-pass UTF-16LE
reads.

- [ ] **Step 5: Implement adapter rendering**

Use `_FPDFBitmap_CreateEx` over adapter-allocated memory, fill and render,
copy with `HEAPU8.slice`, and unconditionally destroy bitmap/page and free
temporary memory.

- [ ] **Step 6: Regenerate resources and run adapter tests**

```bash
export https_proxy=http://127.0.0.1:7890
export http_proxy=http://127.0.0.1:7890
export all_proxy=socks5://127.0.0.1:7891
./scripts/update-pdfium.sh chromium/7961
node scripts/tests/pdfium-web-adapter-test.mjs
node --check pdf-core/src/webMain/resources/pdfium/pdfium-adapter.js
```

Expected: adapter contract passes with zero leaked allocations.

- [ ] **Step 7: Commit**

```bash
git add scripts/pdfium-web scripts/tests pdf-core/src/webMain/resources
git commit -m "feat: add browser PDFium adapter"
```

---

### Task 8: Implement The Shared Web Backend And Kotlin/JS Interop

**Files:**
- Create: `pdf-core/src/webMain/kotlin/io/github/limuyang2/pdf/core/internal/WebPdfiumInterop.kt`
- Create: `pdf-core/src/webMain/kotlin/io/github/limuyang2/pdf/core/internal/WebPdfiumBackend.kt`
- Create: `pdf-core/src/jsMain/kotlin/io/github/limuyang2/pdf/core/internal/JsPdfiumInterop.kt`
- Modify: `pdf-core/src/webMain/kotlin/io/github/limuyang2/pdf/core/internal/PlatformPdfiumCallGate.web.kt`
- Create: `pdf-core/src/jsTest/kotlin/io/github/limuyang2/pdf/core/internal/JsPdfiumBackendTest.kt`

**Interfaces:**
- Produces: pure-Kotlin `WebPdfiumInterop`
- Produces: `internal expect val platformWebPdfiumInterop: WebPdfiumInterop`
- Produces: `WebPdfiumBackend : PdfiumBackend`
- Changes provider: both browser targets return `WebPdfiumBackend`

- [ ] **Step 1: Add failing JS real-backend tests**

Use contract fixtures to verify open, password mapping, information, metadata,
page labels, page information, rendering, text extraction, and close. Also
verify adapter allocation counts return to zero after the final close.

- [ ] **Step 2: Run JS browser tests and verify unavailable-backend failure**

```bash
./gradlew :pdf-core:jsBrowserTest
```

Expected: FAIL because `platformPdfiumBackend()` still returns unavailable.

- [ ] **Step 3: Define the pure-Kotlin interop contract**

Use only Kotlin primitives, `ByteArray`, and internal data classes:

```kotlin
internal interface WebPdfiumInterop {
    suspend fun initialize()
    fun destroy()
    fun open(bytes: ByteArray, password: String?): WebOpenResult
    fun close(handle: Long)
    fun documentInfo(handle: Long): WebDocumentInfo
    fun metadata(handle: Long, tag: String): String?
    fun pageLabel(handle: Long, pageIndex: Int): String?
    fun pageInfo(handle: Long, pageIndex: Int): WebPageInfo
    fun render(
        handle: Long,
        pageIndex: Int,
        request: PdfRenderRequest,
    ): WebRenderResult
    fun extractText(
        handle: Long,
        pageIndex: Int,
        range: PdfTextRange?,
    ): String
}
```

- [ ] **Step 4: Implement the semantic Web backend**

Keep all validation, exception mapping, capability flags, and conversion to
`PdfDocumentInfo`, `PdfPageInfo`, and `OwnedPdfBitmap` in `webMain`.

- [ ] **Step 5: Implement Kotlin/JS adapter calls**

Load `pdfium/pdfium-adapter.js` once by adding a script element relative to
`document.baseURI`, await its Promise, and map the namespaced adapter's plain
JavaScript objects and `Uint8Array` values into the pure-Kotlin contract.

- [ ] **Step 6: Run JS backend tests**

```bash
./gradlew :pdf-core:jsBrowserTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add pdf-core/src/webMain pdf-core/src/jsMain pdf-core/src/jsTest
git commit -m "feat: implement PDFium backend for Kotlin JS"
```

---

### Task 9: Implement Kotlin/Wasm Interop

**Files:**
- Create: `pdf-core/src/wasmJsMain/kotlin/io/github/limuyang2/pdf/core/internal/WasmPdfiumInterop.kt`
- Create: `pdf-core/src/wasmJsTest/kotlin/io/github/limuyang2/pdf/core/internal/WasmPdfiumBackendTest.kt`

**Interfaces:**
- Implements: `platformWebPdfiumInterop` for Wasm
- Reuses: `WebPdfiumBackend` without semantic duplication

- [ ] **Step 1: Add failing Wasm real-backend tests**

Mirror the JS assertions using the same decoded contract fixtures and include
adapter allocation cleanup after final close.

- [ ] **Step 2: Run Wasm browser tests and verify missing actual failure**

```bash
./gradlew :pdf-core:wasmJsBrowserTest
```

Expected: FAIL because the Wasm interop actual is absent.

- [ ] **Step 3: Implement Wasm JavaScript calls**

Use Kotlin/Wasm JavaScript interop only in `wasmJsMain`. Keep `JsAny`,
`JsString`, Promise, and typed-array conversion out of `webMain`.

Provide small `@JsFun` helpers that access only
`globalThis.__pdfViewerPdfium`, then convert results into the exact
`WebPdfiumInterop` data classes.

- [ ] **Step 4: Run Wasm backend tests**

```bash
./gradlew :pdf-core:wasmJsBrowserTest
```

Expected: PASS.

- [ ] **Step 5: Run JS and Wasm together to catch shared-global regressions**

```bash
./gradlew :pdf-core:jsBrowserTest :pdf-core:wasmJsBrowserTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add pdf-core/src/wasmJsMain pdf-core/src/wasmJsTest
git commit -m "feat: implement PDFium backend for Kotlin Wasm"
```

---

### Task 10: Add Cross-Platform CI, Documentation, And Final Verification

**Files:**
- Create: `.github/workflows/pdf-core.yml`
- Modify: `README.md`
- Modify: `docs/pdfium-api-design.md`
- Modify: `docs/pdfium-api-implementation-plan.md`

**Interfaces:**
- Produces: reproducible JVM host and browser validation
- Documents: supported platforms, bundled runtime behavior, and JDK native
  access

- [ ] **Step 1: Add the CI matrix**

Create JVM jobs for:

```text
macos-14       / arm64
macos-15-intel / x64
ubuntu-latest  / x64
windows-latest / x64
```

Each runs `:pdf-core:jvmTest`. A Linux browser job installs Chrome and runs
`:pdf-core:jsBrowserTest` and `:pdf-core:wasmJsBrowserTest`.

- [ ] **Step 2: Update documentation**

Change JVM, JavaScript, and Wasm platform status to available. Document:

- exact supported JVM classifiers;
- browser-only Web support;
- local runtime extraction and SHA verification;
- JNA 5.18.1 as an internal dependency;
- `--enable-native-access=ALL-UNNAMED` for JDK runtimes that enforce explicit
  native access;
- unsupported capabilities.

- [ ] **Step 3: Run formatting and static checks**

```bash
git diff --check
rg -n 'UnavailablePdfiumBackend' \
  pdf-core/src/jvmMain pdf-core/src/webMain pdf-core/src/jsMain \
  pdf-core/src/wasmJsMain
```

Expected: no JVM/Web provider returns `UnavailablePdfiumBackend`.

- [ ] **Step 4: Run the complete Gradle verification matrix available locally**

```bash
./gradlew --no-configuration-cache \
  :pdf-core:allTests \
  :pdf-core:compileKotlinIosArm64 \
  :pdf-core:compileAndroidMain \
  :pdf-core-android-native:assembleRelease \
  :pdf-viewer:assemble \
  :shared:compileKotlinJvm \
  :shared:compileKotlinJs \
  :shared:compileKotlinWasmJs \
  :shared:compileKotlinIosSimulatorArm64
```

Expected: PASS.

- [ ] **Step 5: Run native core verification**

```bash
openssl base64 -d \
  -in pdf-core/src/commonTest/resources/pdf-contract/hello_world.pdf.base64 \
  -out /tmp/pdfviewer-hello-world.pdf
cmake \
  -S pdf-core-native \
  -B /tmp/pdf-core-native-build \
  -DPDFVIEWER_PDFIUM_INCLUDE_DIR="$PWD/pdf-core/src/nativeInterop/cinterop/include" \
  -DPDFVIEWER_PDFIUM_LIBRARY="$PWD/pdf-core/src/jvmMain/resources/pdfium/darwin-aarch64/libpdfium.dylib" \
  -DPDFVIEWER_BUILD_TESTS=ON \
  -DPDFVIEWER_TEST_PDF=/tmp/pdfviewer-hello-world.pdf
cmake --build /tmp/pdf-core-native-build
ctest --test-dir /tmp/pdf-core-native-build --output-on-failure
```

Expected: all native tests pass.

- [ ] **Step 6: Review generated and tracked files**

```bash
git status --short
git diff --check
git diff --stat
```

Verify no Gradle, Kotlin, browser, CMake, or native cache is tracked.

- [ ] **Step 7: Commit**

```bash
git add .github README.md docs pdf-core scripts gradle/libs.versions.toml
git commit -m "test: verify PDFium backends across JVM and web"
```
