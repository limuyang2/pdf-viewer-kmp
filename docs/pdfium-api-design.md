# PDFium KMP API Design

Status: approved scope, API draft
PDFium baseline: `chromium/7961` (`152.0.7961.0`)
Public package: `io.github.limuyang2.pdf.core`

## 1. Scope

The first release is a read-only viewer API. It includes:

- loading from memory and random-access sources;
- password-protected documents;
- document information, metadata, permissions, and page labels;
- page dimensions and rotation;
- full-page and region rendering;
- thumbnails;
- text extraction and text geometry;
- text search;
- bookmarks and destinations;
- page links and URI actions;
- deterministic resource cleanup;
- consistent errors and behavior across Android, JVM, iOS, JS, and Wasm.

The first release does not expose:

- annotation mutation;
- page object mutation;
- document creation or page import;
- form interaction;
- attachments;
- saving;
- progressive network loading or progressive rendering;
- PDF JavaScript execution;
- XFA.

The current PDFium binaries are built with V8 and XFA disabled. Later APIs must
not imply that JavaScript execution or XFA can be enabled at runtime.

## 2. Design principles

1. Public API expresses PDF concepts, not PDFium handles.
2. No `FPDF_*`, pointers, JNI handles, or Emscripten pointers appear in
   `commonMain`.
3. All PDFium entry is globally serialized because PDFium is not thread-safe.
4. Native child handles are scoped to one backend operation whenever possible.
5. Platform image classes are adapters, not core API types.
6. Coordinates, ownership, encoding, and cancellation semantics are explicit.
7. The public API is versioned independently from the bundled PDFium version.
8. Experimental PDFium functions are not exposed as stable API by default.

## 3. Layers

```text
Public commonMain API
        |
        v
Internal semantic backend
        |
        +-- Android/JVM: JNI bridge
        +-- iOS: Kotlin/Native cinterop
        +-- JS/Wasm: Emscripten Module
        |
        v
PDFium FPDF_* API
```

The backend boundary is semantic. Platform implementations may use different
transport mechanisms, but must pass the same contract tests.

## 4. Public entry point

PDFium initialization is process-global and is not exposed as a handle to
normal callers.

```kotlin
public object PdfViewer {
    public val capabilities: PdfCapabilities

    public suspend fun open(
        source: PdfSource,
        password: String? = null,
    ): PdfDocument
}
```

The implementation acquires the internal runtime before opening a document.
It releases the runtime after the last document is closed. Runtime acquire and
release are reference-counted and serialized.

Repeated PDFium initialization is currently harmless, but PDFium itself does
not provide client reference counting. Reference counting therefore belongs to
this library.

## 5. Document sources

```kotlin
public sealed interface PdfSource : AutoCloseable {
    /**
     * The document retains this array until PdfDocument.close().
     * Callers must not mutate it while the document is open.
     */
    public class Bytes(
        public val data: ByteArray,
    ) : PdfSource {
        override fun close() = Unit
    }

    /**
     * PDFium invokes reads synchronously. Implementations must not call back
     * into PdfViewer from read().
     */
    public interface RandomAccess : PdfSource {
        public val size: Long

        public fun read(
            offset: Long,
            destination: ByteArray,
            destinationOffset: Int,
            length: Int,
        ): Int
    }
}
```

Calling `PdfViewer.open()` transfers ownership of the source immediately.
The source is closed exactly once when opening fails, opening is cancelled, or
the resulting document closes. Callers must not reuse or close an owned source.

`read()` returns the number of bytes copied. It must either fill the requested
range or throw an exception. A short read is treated as I/O failure.

Network access is not represented by a suspending `read()` callback because
PDFium's custom file callback is synchronous. Progressive network documents
will use a separate API later.

Platform modules may add adapters:

```kotlin
// Examples, not core API.
public fun Path.asPdfSource(): PdfSource
public fun NSURL.asPdfSource(): PdfSource
public fun File.asPdfSource(): PdfSource
public fun ContentResolver.asPdfSource(uri: Uri): PdfSource
```

## 6. Document API

```kotlin
public class PdfDocument internal constructor(...) : AutoCloseable {
    public val pageCount: Int
    public val isClosed: Boolean

    public operator fun get(pageIndex: Int): PdfPage

    public suspend fun information(): PdfDocumentInfo

    public suspend fun metadata(): PdfMetadata

    public suspend fun bookmarks(): List<PdfBookmark>

    public suspend fun pageLabel(pageIndex: Int): String?

    override fun close()

    public suspend fun closeAndAwait()
}
```

Rules:

- `pageCount` is captured during successful open.
- `get()` validates the index and returns a lightweight descriptor.
- `PdfPage` does not keep an `FPDF_PAGE` open.
- `close()` and `closeAndAwait()` are idempotent and share the same exactly-once
  cleanup path.
- `close()` prevents new operations and waits for the current serialized
  operation to finish before releasing native resources. It returns only after
  the native document, runtime reference, and owned source are released.
- `closeAndAwait()` provides the same completion guarantee without blocking the
  calling thread while waiting for the process-wide gate.
- Calls made after closing throw `PdfClosedException`.
- Closing a document invalidates all page descriptors created from it.

```kotlin
public data class PdfDocumentInfo(
    val version: PdfVersion?,
    val permissions: PdfPermissions,
    val securityRevision: Int?,
    val hasValidCrossReferenceTable: Boolean,
    val isLinearized: Boolean?,
)

public data class PdfVersion(
    val major: Int,
    val minor: Int,
)

public data class PdfMetadata(
    val title: String?,
    val author: String?,
    val subject: String?,
    val keywords: String?,
    val creator: String?,
    val producer: String?,
    val creationDate: String?,
    val modificationDate: String?,
    val additional: Map<String, String> = emptyMap(),
)
```

PDF date strings remain strings in the core module. Parsing them into a date
library type belongs in an optional adapter because malformed and partially
specified PDF dates are common.

```kotlin
public data class PdfPermissions(
    val canPrint: Boolean,
    val canModify: Boolean,
    val canCopy: Boolean,
    val canAnnotate: Boolean,
    val canFillForms: Boolean,
    val canExtractForAccessibility: Boolean,
    val canAssemble: Boolean,
    val canPrintHighQuality: Boolean,
)
```

## 7. Page API

```kotlin
public class PdfPage internal constructor(
    public val document: PdfDocument,
    public val index: Int,
) {
    public suspend fun information(): PdfPageInfo

    public suspend fun render(
        request: PdfRenderRequest,
    ): PdfBitmap

    public suspend fun thumbnail(
        maximumSize: PdfPixelSize,
    ): PdfBitmap?

    public suspend fun extractText(
        range: PdfTextRange? = null,
    ): String

    public suspend fun textLayout(): PdfTextLayout

    public suspend fun search(
        query: String,
        options: PdfSearchOptions = PdfSearchOptions(),
    ): List<PdfSearchMatch>

    public suspend fun links(): List<PdfLink>
}
```

Each operation uses this native sequence under the global PDFium lock:

```text
validate document and page index
load FPDF_PAGE
load any operation-specific child handles
perform the complete operation
copy results into Kotlin-owned or bitmap-owned values
close child handles in reverse order
close FPDF_PAGE
release the global lock
```

The backend may add an internal page cache later, but caching cannot change
public ownership or concurrency behavior.

## 8. Coordinates and geometry

Public page coordinates use PDF page space:

- unit: PDF point, where 72 points equal one inch;
- origin: bottom-left;
- positive X: right;
- positive Y: up;
- values are `Double`;
- rectangles use explicit left, bottom, right, and top edges.

```kotlin
public data class PdfPoint(
    val x: Double,
    val y: Double,
)

public data class PdfSize(
    val width: Double,
    val height: Double,
)

public data class PdfRect(
    val left: Double,
    val bottom: Double,
    val right: Double,
    val top: Double,
)

public data class PdfQuad(
    val first: PdfPoint,
    val second: PdfPoint,
    val third: PdfPoint,
    val fourth: PdfPoint,
)

public data class PdfPixelSize(
    val width: Int,
    val height: Int,
)

public enum class PdfRotation(public val degrees: Int) {
    Degrees0(0),
    Degrees90(90),
    Degrees180(180),
    Degrees270(270),
}

public data class PdfPageInfo(
    val size: PdfSize,
    val rotation: PdfRotation,
    val boundingBox: PdfRect?,
)
```

`PdfPageInfo.rotation` is the intrinsic page rotation. Render requests specify
additional clockwise rotation.

## 9. Rendering

```kotlin
public data class PdfRenderRequest(
    val outputSize: PdfPixelSize,
    val sourceRect: PdfRect? = null,
    val rotation: PdfRotation = PdfRotation.Degrees0,
    val backgroundColor: PdfColor = PdfColor.White,
    val renderAnnotations: Boolean = true,
    val grayscale: Boolean = false,
    val optimizeTextForLcd: Boolean = false,
)

@JvmInline
public value class PdfColor(public val argb: UInt) {
    public companion object {
        public val Transparent: PdfColor
        public val White: PdfColor
        public val Black: PdfColor
    }
}
```

`sourceRect == null` renders the complete page. A non-null source rectangle
renders that page-space region into the entire output bitmap, enabling tiled
rendering without exposing PDFium matrices.

The renderer applies intrinsic page rotation first and request rotation second.

LCD text optimization defaults to false because it is unsuitable for
transparent output and is not consistently desirable across display types.

### Bitmap result

```kotlin
public interface PdfBitmap : AutoCloseable {
    public val width: Int
    public val height: Int
    public val stride: Int
    public val format: PdfPixelFormat
    public val isClosed: Boolean

    public fun copyPixels(): ByteArray

    public fun copyPixels(
        destination: ByteArray,
        destinationOffset: Int = 0,
    )

    override fun close()
}

public enum class PdfPixelFormat {
    Bgra8888,
    Bgrx8888,
    Gray8,
}
```

The `FPDF_BITMAP` handle is destroyed before `render()` returns. `PdfBitmap`
owns only the resulting pixel storage. This prevents a rendered image from
keeping the PDFium runtime or document alive.

Platform or Compose adapters are separate:

```kotlin
public fun PdfBitmap.toImageBitmap(): ImageBitmap
public fun PdfBitmap.toAndroidBitmap(): Bitmap
public fun PdfBitmap.toUIImage(): UIImage
```

The core module does not expose platform image types.

## 10. Text

PDFium character indexes are not guaranteed to equal Kotlin string indexes.
The API therefore names and models character indexes explicitly.

```kotlin
public data class PdfTextRange(
    val startCharacterIndex: Int,
    val characterCount: Int,
)

public class PdfTextLayout internal constructor(...) {
    public val text: String
    public val characterCount: Int

    public operator fun get(characterIndex: Int): PdfCharacter

    public fun bounds(
        range: PdfTextRange,
    ): List<PdfRect>
}

public data class PdfCharacter(
    val characterIndex: Int,
    val unicodeCodePoint: Int,
    val bounds: PdfRect?,
    val origin: PdfPoint?,
    val fontSize: Double?,
    val angleRadians: Double?,
    val isGenerated: Boolean,
    val isHyphen: Boolean,
    val hasUnicodeMappingError: Boolean,
)
```

`extractText()` is the inexpensive path when geometry is not needed.
`textLayout()` captures the text and character geometry into an immutable
snapshot and closes `FPDF_TEXTPAGE` before returning.

## 11. Search

```kotlin
public data class PdfSearchOptions(
    val matchCase: Boolean = false,
    val matchWholeWord: Boolean = false,
    val consecutive: Boolean = false,
)

public data class PdfSearchMatch(
    val range: PdfTextRange,
    val bounds: List<PdfRect>,
)
```

Search results are immutable snapshots. `FPDF_SCHHANDLE` never escapes the
backend operation.

An empty query is rejected with `IllegalArgumentException`. Search result order
is document text order.

## 12. Links, destinations, and bookmarks

```kotlin
public data class PdfLink(
    val bounds: List<PdfQuad>,
    val target: PdfLinkTarget,
)

public sealed interface PdfLinkTarget {
    public data class Internal(
        val destination: PdfDestination,
    ) : PdfLinkTarget

    public data class Uri(
        val uri: String,
    ) : PdfLinkTarget

    public data class RemoteDocument(
        val filePath: String?,
        val destination: PdfDestination?,
    ) : PdfLinkTarget

    public data class Unsupported(
        val nativeActionType: Int,
    ) : PdfLinkTarget
}

public data class PdfDestination(
    val pageIndex: Int,
    val view: PdfDestinationView,
)

public sealed interface PdfDestinationView {
    data class Unknown(
        val nativeViewMode: Int,
        val parameters: List<Double>,
    ) : PdfDestinationView

    data class Xyz(
        val x: Double?,
        val y: Double?,
        val zoom: Double?,
    ) : PdfDestinationView

    data object FitPage : PdfDestinationView
    data class FitHorizontally(val top: Double?) : PdfDestinationView
    data class FitVertically(val left: Double?) : PdfDestinationView
    data class FitRectangle(val bounds: PdfRect) : PdfDestinationView
    data object FitBoundingBox : PdfDestinationView
    data class FitBoundingBoxHorizontally(val top: Double?) : PdfDestinationView
    data class FitBoundingBoxVertically(val left: Double?) : PdfDestinationView
}

public data class PdfBookmark(
    val title: String,
    val destination: PdfDestination?,
    val target: PdfLinkTarget?,
    val color: PdfColor?,
    val children: List<PdfBookmark>,
)
```

URI strings are returned as document data. The library never opens a URI or
performs an external action.

Bookmark traversal must be iterative internally to avoid stack overflow on
maliciously deep bookmark trees. The backend also enforces configurable node
and depth limits.

## 13. Errors

Expected PDF failures use typed exceptions. Programming errors continue to use
standard Kotlin exceptions.

```kotlin
public sealed class PdfException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

public class PdfPasswordRequiredException : PdfException(...)
public class PdfIncorrectPasswordException : PdfException(...)
public class PdfInvalidFormatException : PdfException(...)
public class PdfUnsupportedSecurityException : PdfException(...)
public class PdfIoException(...) : PdfException(...)
public class PdfPageException(
    public val pageIndex: Int,
    ...
) : PdfException(...)
public class PdfClosedException(...) : PdfException(...)
public class PdfUnsupportedFeatureException(...) : PdfException(...)
public class PdfNativeException(
    public val nativeErrorCode: Int,
    ...
) : PdfException(...)
```

PDFium error mapping:

| PDFium error | Public error |
| --- | --- |
| `FPDF_ERR_FILE` | `PdfIoException` |
| `FPDF_ERR_FORMAT` | `PdfInvalidFormatException` |
| `FPDF_ERR_PASSWORD` with no supplied password | `PdfPasswordRequiredException` |
| `FPDF_ERR_PASSWORD` with supplied password | `PdfIncorrectPasswordException` |
| `FPDF_ERR_SECURITY` | `PdfUnsupportedSecurityException` |
| `FPDF_ERR_PAGE` | `PdfPageException` |
| unknown value | `PdfNativeException` |

`FPDF_GetLastError()` is read while still holding the global PDFium lock,
immediately after the failed operation.

## 14. Concurrency and cancellation

Public operations that may parse, render, search, or perform I/O are
suspending. Internal backend calls are synchronous because PDFium and the
currently supported local sources expose synchronous APIs.

Platform execution:

- Android/JVM: dedicated serial worker dispatcher;
- iOS: dedicated serial queue or an equivalent locked worker;
- JS/Wasm: serialized browser event-loop execution.

The serialization scope is process-wide, not per document.
No backend call may suspend while holding this gate. This guarantees that
JavaScript cannot interleave `close()` with an active PDFium call and removes
the need for deferred-close queues.

Cancellation rules:

- cancellation before an operation starts prevents native entry;
- ordinary PDFium render and parse calls cannot be interrupted safely;
- cancellation is checked again after native completion and before publishing
  the result;
- true mid-render cancellation is reserved for the later progressive-render
  API.

`PdfDocument.close()` is thread-safe and may block while an operation is
already in progress. `closeAndAwait()` suspends for the same condition. Neither
method may race native destruction against rendering, and every concurrent
closer returns only after cleanup completes.

## 15. Internal backend

Native handles are internal value types with no public conversion:

```kotlin
@JvmInline
internal value class NativeDocumentHandle(val value: Long)

internal interface PdfiumBackend {
    val capabilities: PdfCapabilities

    fun open(
        source: PdfSource,
        password: String?,
    ): OpenedDocument

    fun close(document: NativeDocumentHandle)

    fun documentInformation(
        document: NativeDocumentHandle,
    ): PdfDocumentInfo

    fun metadata(
        document: NativeDocumentHandle,
    ): PdfMetadata

    fun pageInformation(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfPageInfo

    fun render(
        document: NativeDocumentHandle,
        pageIndex: Int,
        request: PdfRenderRequest,
    ): PdfBitmap

    fun extractText(
        document: NativeDocumentHandle,
        pageIndex: Int,
        range: PdfTextRange?,
    ): String

    fun textLayout(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfTextLayout

    fun search(
        document: NativeDocumentHandle,
        pageIndex: Int,
        query: String,
        options: PdfSearchOptions,
    ): List<PdfSearchMatch>

    fun links(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): List<PdfLink>

    fun bookmarks(
        document: NativeDocumentHandle,
    ): List<PdfBookmark>
}
```

The actual interface may be split into focused internal interfaces when
implemented. The public API must not depend on that split.
Backend implementations must not suspend or invoke document lifecycle methods
reentrantly. Public suspend functions perform dispatcher selection,
serialization, and cancellation checks around these synchronous calls.

## 16. Native bridge ABI

Android, JVM, and iOS share the same narrow native bridge implementation.
Android and JVM use JNI adapters while iOS binds the C ABI through cinterop.
Platform adapters perform data conversion only and contain no direct
`FPDF_*` calls.

The bridge follows these rules:

- C-compatible ABI;
- opaque document handles;
- fixed-width integer fields;
- no C++ exceptions across the ABI;
- no STL types across the ABI;
- explicit status code for every fallible operation;
- caller-provided output buffers where practical;
- no cross-runtime allocator ownership unless paired with an explicit free;
- UTF-8 for metadata and URIs;
- UTF-16 code units for PDFium text extraction;
- internal ABI version constant.

```c
typedef struct pdfv_document pdfv_document_t;

typedef enum pdfv_status {
  PDFV_OK = 0,
  PDFV_ERROR_UNKNOWN = 1,
  PDFV_ERROR_IO = 2,
  PDFV_ERROR_FORMAT = 3,
  PDFV_ERROR_PASSWORD = 4,
  PDFV_ERROR_SECURITY = 5,
  PDFV_ERROR_PAGE = 6,
  PDFV_ERROR_INVALID_ARGUMENT = 7,
  PDFV_ERROR_CLOSED = 8,
  PDFV_ERROR_BUFFER_TOO_SMALL = 9,
  PDFV_ERROR_UNSUPPORTED = 10,
} pdfv_status_t;
```

Initial bridge operations:

```text
pdfv_get_abi_version
pdfv_runtime_acquire
pdfv_runtime_release
pdfv_document_open_memory
pdfv_document_open_access
pdfv_document_close
pdfv_document_get_page_count
pdfv_document_get_information
pdfv_document_get_metadata
pdfv_document_get_page_label
pdfv_document_get_bookmarks
pdfv_page_get_information
pdfv_page_render
pdfv_page_get_thumbnail
pdfv_page_extract_text
pdfv_page_get_text_layout
pdfv_page_search
pdfv_page_get_links
pdfv_buffer_free
```

An operation that uses a page owns the complete
`FPDF_LoadPage()`/`FPDF_ClosePage()` sequence. JNI never exposes a page,
text-page, search, link, destination, bookmark, or bitmap PDFium handle.

The iOS implementation may call PDFium directly through cinterop, but it must
match the same semantic operation boundaries. JS and Wasm mirror the same
contract over the Emscripten module.

## 17. Capabilities

```kotlin
public data class PdfCapabilities(
    val text: Boolean,
    val search: Boolean,
    val bookmarks: Boolean,
    val links: Boolean,
    val thumbnails: Boolean,
    val progressiveLoading: Boolean,
    val progressiveRendering: Boolean,
    val forms: Boolean,
    val editing: Boolean,
    val javascriptExecution: Boolean,
    val xfa: Boolean,
)
```

Capabilities describe the loaded backend and PDFium build, not what this API
version currently exposes. For the bundled build, JavaScript execution and XFA
are false.

## 18. Defensive limits

PDF files are untrusted input. The backend applies configurable internal limits
to:

- output bitmap dimensions and total byte count;
- bookmark depth and node count;
- link and rectangle counts;
- extracted text character count;
- metadata string length;
- random-access file size supported by the target ABI.

Limit failures use `PdfUnsupportedFeatureException` or a dedicated resource
limit subtype before allocating excessive memory.

## 19. Testing

### Common tests

- public validation and closed-state behavior with a fake backend;
- PDFium error mapping;
- page index validation;
- geometry and rotation rules;
- source lifetime behavior;
- runtime acquire/release reference counting;
- cancellation at operation boundaries.

### Backend contract tests

The same fixture suite runs on every supported backend:

- open valid, corrupt, encrypted, and unsupported-security documents;
- page count, dimensions, rotation, labels, and metadata;
- full and region render;
- transparent and opaque render;
- Unicode text and surrogate pairs;
- generated characters and hyphens;
- case-sensitive and whole-word search;
- internal, URI, and unsupported link actions;
- nested and malformed bookmarks;
- repeated close;
- close racing a render;
- concurrent calls from multiple documents, verifying global serialization.

### Rendering tests

Use small committed fixtures and golden images. Compare dimensions, stride,
pixel format, and pixel data with a documented tolerance where platform font
fallback can differ.

Official PDFium embedder tests under `fpdfsdk/*_embeddertest.cpp` are the
behavior reference for each wrapped function.

## 20. Planned extension boundaries

Future functionality extends the API through focused sessions:

```text
PdfFormSession
PdfAnnotationSession
PdfEditSession
PdfProgressiveDocument
PdfProgressiveRender
PdfSaveTarget
```

Editing APIs must not make the read-only `PdfPage` retain mutable native
handles. A mutable session owns its document exclusively and defines explicit
save/close behavior.
