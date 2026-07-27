# Changelog

[简体中文](CHANGELOG.zh-CN.md)

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
