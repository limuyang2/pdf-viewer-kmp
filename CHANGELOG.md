# Changelog

[简体中文](CHANGELOG.zh-CN.md)

## 0.2.1 - 2026-07-29

This release fixes iOS build and runtime integration issues.

### PDF Core

- Embed `libpdfviewer_core.a` in the cinterop KLIB to resolve missing `pdfv_*`
  symbols when linking final Kotlin/Native frameworks.
- Ensure device arm64 and simulator arm64 builds generate and include the
  native PDF bridge for the selected architecture.

### iOS Demo

- Copy the platform-specific `libpdfium.dylib` into the app's `Frameworks`
  directory and code-sign it for device and simulator builds.
- Ensure Xcode Kotlin build support skips only the redundant Gradle invocation,
  not PDFium embedding, preventing
  `Library not loaded: @rpath/libpdfium.dylib` at launch.

## 0.2.0 - 2026-07-28

This release adds document search support across PDF Core and PDF Viewer.

### PDF Core

- Add text search support on Android, JVM, iOS, JavaScript, and WasmJS.
- Support case-sensitive, whole-word, and consecutive-match search options.
- Return the text range and page-space bounds for each search match.

### PDF Viewer

- Add document search state and lifecycle management to `PdfViewState`.
- Report incremental search progress, results, selection, completion, and
  failures.
- Add previous, next, and direct result selection.
- Highlight all search results and distinguish the currently selected result.
- Make match and selected-match fill, stroke, corner radius, and padding
  configurable through `PdfSearchHighlightStyle`.
- Add immediate and animated scrolling to the exact position of a search
  result, including horizontal positioning on zoomed pages.
- Support configurable search-result alignment within the viewport.
- Add a search test page to the shared demo.

## 0.1.0 - 2026-07-27

The first public release of `pdf-core` and `pdf-viewer`.

### PDF Core

- Open PDFs from byte arrays, including password-protected documents.
- Read page count, PDF version, permissions, metadata, and page labels.
- Read page dimensions, intrinsic rotation, and bounding boxes.
- Render complete pages as BGRA8888 bitmaps.
- Configure background color, rotation, annotations, grayscale, and LCD text
  rendering.
- Extract basic page text.
- Read internal page links, URI links, and link bounds.
- Search page text on Android.
- Manage document, page, and bitmap lifecycles through a consistent exception
  model.

### PDF Viewer

- Display PDFs with the Compose Multiplatform `PdfView` component.
- Lazily load vertically scrolling pages with horizontal scrolling support.
- Zoom with a two-pointer gesture and configure the maximum zoom.
- Disable gesture zoom while retaining programmatic zoom controls.
- Ignore pan during two-pointer zoom to reduce visual jitter.
- Configure page spacing, padding, color, and borders.
- Limit the maximum rendered bitmap dimension.
- Customize page loading and rendering error UI.
- Navigate internal page links and handle URI links.
- Control page navigation, zoom, and the rendered-page cache.

### Supported platforms

- Android: arm32, arm64, x86, and x64.
- iOS: device arm64 and simulator arm64.
- JVM: macOS arm64/x64, Linux x64, and Windows x64.
- Browser: JavaScript and Wasm.

### Current limitations

- Bookmarks, thumbnails, forms, and PDF editing are not yet supported.
- Random-access sources and cropped rendering are not yet supported.
- Text search is currently available only on Android.
- The viewer does not yet support text selection, search highlighting, or
  tiled rendering.
- PDF JavaScript and XFA are not supported.
