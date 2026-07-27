# PDF Page Appearance Configuration

## Goal

Allow callers of `PdfView` to configure each PDF page's background,
border, loading content, and error content without taking ownership of
page sizing, rendering, caching, or link handling.

Existing callers must keep the current appearance and behavior without
source changes.

## Public API

`PdfView` keeps the existing `pageColor` parameter and appends:

```kotlin
pageBorder: BorderStroke? =
    BorderStroke(1.dp, Color(0x22000000)),
pageLoadingContent:
    @Composable BoxScope.(pageIndex: Int) -> Unit = {},
pageErrorContent:
    (@Composable BoxScope.(
        pageIndex: Int,
        error: Throwable,
    ) -> Unit)? = null,
```

The new parameters are appended after all existing parameters. Existing
named and positional calls therefore remain source compatible.

## Behavior

- `pageColor` is applied to loading, rendered, and failed pages.
- A non-null `pageBorder` is applied to loading, rendered, and failed
  pages.
- `pageBorder = null` omits the border modifier entirely.
- `pageLoadingContent` is displayed while page information is loading
  and while the page bitmap is rendering.
- `pageErrorContent` is displayed when either page information loading
  or bitmap rendering fails. When it is `null`, `PdfView` uses its
  existing built-in error content; callers can pass an empty lambda to
  hide error content.
- Both content slots use the page-sized `BoxScope`.
- The error slot receives the zero-based page index and the original
  failure.
- Existing `onPageError` behavior remains unchanged: it is invoked once
  when a page operation fails, independently of the visual error slot.
- Rendering, cache management, gestures, and link activation remain
  internal to `PdfView`.

## Defaults

- Page color remains `Color.White`.
- Page border remains `1.dp` with `Color(0x22000000)`.
- Loading content remains visually empty.
- Error content keeps the existing red text and distinguishes page
  information failures from bitmap rendering failures through the
  default message.

To preserve that distinction while exposing a single public error slot,
the internal page failure state retains the original `Throwable`.
`PdfView` chooses the existing information-loading or bitmap-rendering
default when the custom slot is `null`.

## Internal Structure

A shared page-container modifier applies width, aspect ratio,
background, and optional border in one place. Loading, ready, and error
states use this same decoration path so their appearance cannot drift.

The page information and render state models retain failures rather
than using marker-only failed states. This supplies the original
exception to `pageErrorContent`.

## Verification

- Add tests for optional border application and shared decoration
  construction where the existing test environment permits direct
  modifier inspection.
- Add tests for retaining original failures in page state where it can
  be exercised without mocking platform PDF rendering.
- Compile common, JVM, Android, JS, Wasm, and iOS source sets to verify
  that the public Compose API is valid across targets.
- Run the existing `pdf-viewer` common tests to guard rendering math,
  state, links, transforms, and cache behavior.
