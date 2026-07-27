#include "pdfviewer_core.h"

#include <algorithm>
#include <limits>
#include <memory>
#include <new>
#include <string>
#include <unordered_set>
#include <vector>

#include "fpdf_doc.h"
#include "fpdf_edit.h"
#include "fpdf_text.h"
#include "fpdfview.h"

struct pdfv_document {
  std::vector<uint8_t> bytes;
  FPDF_DOCUMENT handle = nullptr;

  ~pdfv_document() {
    if (handle) {
      FPDF_CloseDocument(handle);
    }
  }
};

struct pdfv_search_result {
  std::vector<pdfv_search_match_t> matches;
  std::vector<pdfv_rect_t> rects;
};

namespace {

bool g_initialized = false;
std::unordered_set<pdfv_document_t*> g_documents;
std::unordered_set<pdfv_search_result_t*> g_search_results;

class ScopedPage {
 public:
  explicit ScopedPage(FPDF_PAGE page) : page_(page) {}
  ~ScopedPage() {
    if (page_) {
      FPDF_ClosePage(page_);
    }
  }

  FPDF_PAGE get() const { return page_; }

 private:
  FPDF_PAGE page_;
};

class ScopedTextPage {
 public:
  explicit ScopedTextPage(FPDF_TEXTPAGE page) : page_(page) {}
  ~ScopedTextPage() {
    if (page_) {
      FPDFText_ClosePage(page_);
    }
  }

  FPDF_TEXTPAGE get() const { return page_; }

 private:
  FPDF_TEXTPAGE page_;
};

class ScopedSearch {
 public:
  explicit ScopedSearch(FPDF_SCHHANDLE search) : search_(search) {}
  ~ScopedSearch() {
    if (search_) {
      FPDFText_FindClose(search_);
    }
  }

  FPDF_SCHHANDLE get() const { return search_; }

 private:
  FPDF_SCHHANDLE search_;
};

class ScopedBitmap {
 public:
  explicit ScopedBitmap(FPDF_BITMAP bitmap) : bitmap_(bitmap) {}
  ~ScopedBitmap() {
    if (bitmap_) {
      FPDFBitmap_Destroy(bitmap_);
    }
  }

  FPDF_BITMAP get() const { return bitmap_; }

 private:
  FPDF_BITMAP bitmap_;
};

pdfv_status_t MapPdfiumError(unsigned long error) {
  switch (error) {
    case FPDF_ERR_FILE:
      return PDFV_ERROR_IO;
    case FPDF_ERR_FORMAT:
      return PDFV_ERROR_FORMAT;
    case FPDF_ERR_PASSWORD:
      return PDFV_ERROR_PASSWORD;
    case FPDF_ERR_SECURITY:
      return PDFV_ERROR_SECURITY;
    case FPDF_ERR_PAGE:
      return PDFV_ERROR_PAGE;
    case FPDF_ERR_SUCCESS:
      return PDFV_ERROR_UNKNOWN;
    case FPDF_ERR_UNKNOWN:
    default:
      return PDFV_ERROR_UNKNOWN;
  }
}

pdfv_status_t RequireDocument(pdfv_document_t* document) {
  if (!document || g_documents.find(document) == g_documents.end()) {
    return PDFV_ERROR_CLOSED;
  }
  return PDFV_OK;
}

pdfv_status_t RequireSearchResult(pdfv_search_result_t* result) {
  if (!result || g_search_results.find(result) == g_search_results.end()) {
    return PDFV_ERROR_CLOSED;
  }
  return PDFV_OK;
}

bool FitsUtf16ByteCount(size_t units) {
  return units <= std::numeric_limits<unsigned long>::max() / 2;
}

struct CollectedLink {
  pdfv_link_t link{};
  std::vector<pdfv_quad_t> quads;
  std::string string;
};

void ReadDestination(FPDF_DOCUMENT document,
                     FPDF_DEST destination,
                     pdfv_destination_t* result) {
  *result = {};
  result->page_index = FPDFDest_GetDestPageIndex(document, destination);
  unsigned long parameter_count = 0;
  FS_FLOAT parameters[4] = {};
  result->view_mode =
      static_cast<uint32_t>(
          FPDFDest_GetView(destination, &parameter_count, parameters));
  result->parameter_count =
      static_cast<uint32_t>(std::min<unsigned long>(parameter_count, 4));
  for (uint32_t index = 0; index < result->parameter_count; ++index) {
    result->parameters[index] = parameters[index];
  }

  FPDF_BOOL has_x = 0;
  FPDF_BOOL has_y = 0;
  FPDF_BOOL has_zoom = 0;
  FS_FLOAT x = 0;
  FS_FLOAT y = 0;
  FS_FLOAT zoom = 0;
  if (FPDFDest_GetLocationInPage(destination, &has_x, &has_y, &has_zoom,
                                 &x, &y, &zoom)) {
    result->has_x = has_x;
    result->has_y = has_y;
    result->has_zoom = has_zoom;
    result->x = x;
    result->y = y;
    result->zoom = zoom;
  }
}

template <typename Read>
std::string ReadUtf8(Read read) {
  const unsigned long required_bytes = read(nullptr, 0);
  if (required_bytes <= 1) {
    return {};
  }
  std::vector<char> buffer(required_bytes);
  if (read(buffer.data(), required_bytes) != required_bytes) {
    return {};
  }
  return std::string(buffer.data(), required_bytes - 1);
}

std::vector<pdfv_quad_t> ReadLinkQuads(FPDF_LINK link) {
  std::vector<pdfv_quad_t> result;
  const int quad_count = FPDFLink_CountQuadPoints(link);
  if (quad_count > 0) {
    result.reserve(static_cast<size_t>(quad_count));
    for (int index = 0; index < quad_count; ++index) {
      FS_QUADPOINTSF value{};
      if (FPDFLink_GetQuadPoints(link, index, &value)) {
        result.push_back(
            {value.x1, value.y1, value.x2, value.y2, value.x3, value.y3,
             value.x4, value.y4});
      }
    }
  }
  if (result.empty()) {
    FS_RECTF rect{};
    if (FPDFLink_GetAnnotRect(link, &rect)) {
      result.push_back(
          {rect.left, rect.top, rect.right, rect.top, rect.left, rect.bottom,
           rect.right, rect.bottom});
    }
  }
  return result;
}

CollectedLink ReadLink(FPDF_DOCUMENT document, FPDF_LINK link) {
  CollectedLink result;
  result.quads = ReadLinkQuads(link);

  FPDF_DEST destination = FPDFLink_GetDest(document, link);
  if (destination) {
    result.link.target_type = PDFV_LINK_TARGET_INTERNAL;
    ReadDestination(document, destination, &result.link.destination);
    return result;
  }

  FPDF_ACTION action = FPDFLink_GetAction(link);
  const unsigned long action_type =
      action ? FPDFAction_GetType(action) : PDFACTION_UNSUPPORTED;
  result.link.native_action_type = static_cast<uint32_t>(action_type);
  switch (action_type) {
    case PDFACTION_GOTO:
      destination = FPDFAction_GetDest(document, action);
      if (destination) {
        result.link.target_type = PDFV_LINK_TARGET_INTERNAL;
        ReadDestination(document, destination, &result.link.destination);
      } else {
        result.link.target_type = PDFV_LINK_TARGET_UNSUPPORTED;
      }
      break;
    case PDFACTION_URI:
      result.link.target_type = PDFV_LINK_TARGET_URI;
      result.string = ReadUtf8([&](void* buffer, unsigned long length) {
        return FPDFAction_GetURIPath(document, action, buffer, length);
      });
      if (result.string.empty()) {
        result.link.target_type = PDFV_LINK_TARGET_UNSUPPORTED;
      }
      break;
    case PDFACTION_REMOTEGOTO:
      result.link.target_type = PDFV_LINK_TARGET_REMOTE_DOCUMENT;
      result.string = ReadUtf8([&](void* buffer, unsigned long length) {
        return FPDFAction_GetFilePath(action, buffer, length);
      });
      break;
    default:
      result.link.target_type = PDFV_LINK_TARGET_UNSUPPORTED;
      break;
  }
  return result;
}

template <typename Operation>
pdfv_status_t Guard(Operation operation) {
  try {
    return operation();
  } catch (const std::bad_alloc&) {
    return PDFV_ERROR_OUT_OF_MEMORY;
  } catch (...) {
    return PDFV_ERROR_UNKNOWN;
  }
}

}  // namespace

uint32_t pdfv_get_abi_version(void) {
  return PDFV_ABI_VERSION;
}

pdfv_status_t pdfv_initialize(void) {
  return Guard([] {
    if (g_initialized) {
      return PDFV_OK;
    }
    FPDF_LIBRARY_CONFIG config{};
    config.version = 2;
    FPDF_InitLibraryWithConfig(&config);
    g_initialized = true;
    return PDFV_OK;
  });
}

pdfv_status_t pdfv_destroy(void) {
  return Guard([] {
    if (!g_initialized) {
      return PDFV_OK;
    }
    if (!g_documents.empty() || !g_search_results.empty()) {
      return PDFV_ERROR_INVALID_STATE;
    }
    FPDF_DestroyLibrary();
    g_initialized = false;
    return PDFV_OK;
  });
}

pdfv_status_t pdfv_open_memory(const uint8_t* data,
                               size_t size,
                               const char* password_utf8,
                               pdfv_document_t** document,
                               int32_t* page_count,
                               uint32_t* pdfium_error) {
  return Guard([&] {
    if (!document || !page_count || !pdfium_error) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }
    *document = nullptr;
    *page_count = 0;
    *pdfium_error = 0;
    if (!g_initialized) {
      return PDFV_ERROR_INVALID_STATE;
    }
    if (!data || size == 0) {
      return PDFV_ERROR_FORMAT;
    }

    auto opened = std::make_unique<pdfv_document_t>();
    opened->bytes.assign(data, data + size);
    opened->handle = FPDF_LoadMemDocument64(
        opened->bytes.data(), opened->bytes.size(), password_utf8);
    if (!opened->handle) {
      const unsigned long error = FPDF_GetLastError();
      *pdfium_error = static_cast<uint32_t>(error);
      return MapPdfiumError(error);
    }

    const int count = FPDF_GetPageCount(opened->handle);
    if (count < 0) {
      return PDFV_ERROR_UNKNOWN;
    }
    g_documents.insert(opened.get());
    *page_count = count;
    *document = opened.release();
    return PDFV_OK;
  });
}

pdfv_status_t pdfv_close_document(pdfv_document_t* document) {
  return Guard([&] {
    if (RequireDocument(document) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    g_documents.erase(document);
    delete document;
    return PDFV_OK;
  });
}

pdfv_status_t pdfv_get_document_info(pdfv_document_t* document,
                                     pdfv_document_info_t* info) {
  return Guard([&] {
    if (RequireDocument(document) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    if (!info) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }

    *info = {};
    int version = 0;
    if (FPDF_GetFileVersion(document->handle, &version)) {
      info->has_version = 1;
      info->version = version;
    }
    info->permissions =
        static_cast<uint64_t>(FPDF_GetDocPermissions(document->handle));
    info->security_revision =
        FPDF_GetSecurityHandlerRevision(document->handle);
    info->has_valid_cross_reference_table =
        FPDF_DocumentHasValidCrossReferenceTable(document->handle) ? 1 : 0;
    info->is_linearized = -1;
    return PDFV_OK;
  });
}

pdfv_status_t pdfv_get_metadata_utf16(pdfv_document_t* document,
                                      const char* tag,
                                      uint16_t* buffer,
                                      size_t buffer_units,
                                      size_t* required_units) {
  return Guard([&] {
    if (RequireDocument(document) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    if (!tag || !required_units || !FitsUtf16ByteCount(buffer_units)) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }

    const unsigned long required_bytes =
        FPDF_GetMetaText(document->handle, tag, nullptr, 0);
    *required_units = static_cast<size_t>(required_bytes / 2);
    if (required_bytes == 0) {
      return PDFV_OK;
    }
    if (required_bytes % 2 != 0) {
      return PDFV_ERROR_UNKNOWN;
    }
    if (!buffer || buffer_units < *required_units) {
      return PDFV_ERROR_BUFFER_TOO_SMALL;
    }
    const unsigned long written = FPDF_GetMetaText(
        document->handle, tag, buffer,
        static_cast<unsigned long>(buffer_units * 2));
    return written == required_bytes ? PDFV_OK : PDFV_ERROR_UNKNOWN;
  });
}

pdfv_status_t pdfv_get_page_label_utf16(pdfv_document_t* document,
                                        int32_t page_index,
                                        uint16_t* buffer,
                                        size_t buffer_units,
                                        size_t* required_units) {
  return Guard([&] {
    if (RequireDocument(document) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    if (!required_units || !FitsUtf16ByteCount(buffer_units)) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }

    const unsigned long required_bytes = FPDF_GetPageLabel(
        document->handle, page_index, nullptr, 0);
    *required_units = static_cast<size_t>(required_bytes / 2);
    if (required_bytes == 0) {
      return PDFV_OK;
    }
    if (required_bytes % 2 != 0) {
      return PDFV_ERROR_UNKNOWN;
    }
    if (!buffer || buffer_units < *required_units) {
      return PDFV_ERROR_BUFFER_TOO_SMALL;
    }
    const unsigned long written =
        FPDF_GetPageLabel(document->handle, page_index, buffer,
                          static_cast<unsigned long>(buffer_units * 2));
    return written == required_bytes ? PDFV_OK : PDFV_ERROR_UNKNOWN;
  });
}

pdfv_status_t pdfv_get_page_info(pdfv_document_t* document,
                                 int32_t page_index,
                                 pdfv_page_info_t* info) {
  return Guard([&] {
    if (RequireDocument(document) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    if (!info) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }

    ScopedPage page(FPDF_LoadPage(document->handle, page_index));
    if (!page.get()) {
      return PDFV_ERROR_PAGE;
    }
    *info = {};
    info->width = FPDF_GetPageWidthF(page.get());
    info->height = FPDF_GetPageHeightF(page.get());
    info->rotation = FPDFPage_GetRotation(page.get());
    FS_RECTF bounds{};
    if (FPDF_GetPageBoundingBox(page.get(), &bounds)) {
      info->has_bounding_box = 1;
      info->bounding_box.left = bounds.left;
      info->bounding_box.bottom = bounds.bottom;
      info->bounding_box.right = bounds.right;
      info->bounding_box.top = bounds.top;
    }
    return PDFV_OK;
  });
}

pdfv_status_t pdfv_render_page(pdfv_document_t* document,
                               int32_t page_index,
                               const pdfv_render_request_t* request,
                               uint8_t* pixels,
                               size_t pixels_size) {
  return Guard([&] {
    if (RequireDocument(document) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    if (!request || !pixels || request->width <= 0 || request->height <= 0 ||
        request->rotation < 0 || request->rotation > 3) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }
    const size_t width = static_cast<size_t>(request->width);
    const size_t height = static_cast<size_t>(request->height);
    if (width > static_cast<size_t>(std::numeric_limits<int>::max()) / 4 ||
        height > std::numeric_limits<size_t>::max() / (width * 4)) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }
    const size_t stride = width * 4;
    const size_t required_size = stride * height;
    if (pixels_size < required_size ||
        stride > static_cast<size_t>(std::numeric_limits<int>::max())) {
      return PDFV_ERROR_BUFFER_TOO_SMALL;
    }

    ScopedPage page(FPDF_LoadPage(document->handle, page_index));
    if (!page.get()) {
      return PDFV_ERROR_PAGE;
    }
    ScopedBitmap bitmap(FPDFBitmap_CreateEx(
        request->width, request->height, FPDFBitmap_BGRA, pixels,
        static_cast<int>(stride)));
    if (!bitmap.get()) {
      return PDFV_ERROR_OUT_OF_MEMORY;
    }
    if (!FPDFBitmap_FillRect(bitmap.get(), 0, 0, request->width,
                             request->height, request->background_argb)) {
      return PDFV_ERROR_UNKNOWN;
    }

    int flags = 0;
    if (request->flags & PDFV_RENDER_ANNOTATIONS) {
      flags |= FPDF_ANNOT;
    }
    if (request->flags & PDFV_RENDER_GRAYSCALE) {
      flags |= FPDF_GRAYSCALE;
    }
    if (request->flags & PDFV_RENDER_LCD_TEXT) {
      flags |= FPDF_LCD_TEXT;
    }
    FPDF_RenderPageBitmap(bitmap.get(), page.get(), 0, 0, request->width,
                          request->height, request->rotation, flags);
    return PDFV_OK;
  });
}

pdfv_status_t pdfv_extract_text_utf16(pdfv_document_t* document,
                                      int32_t page_index,
                                      int32_t start_character_index,
                                      int32_t character_count,
                                      uint16_t* buffer,
                                      size_t buffer_units,
                                      size_t* required_units) {
  return Guard([&] {
    if (RequireDocument(document) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    if (!required_units || start_character_index < 0) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }
    ScopedPage page(FPDF_LoadPage(document->handle, page_index));
    if (!page.get()) {
      return PDFV_ERROR_PAGE;
    }
    ScopedTextPage text_page(FPDFText_LoadPage(page.get()));
    if (!text_page.get()) {
      return PDFV_ERROR_PAGE;
    }

    const int total_count = FPDFText_CountChars(text_page.get());
    if (total_count < 0 || start_character_index > total_count) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }
    const int available = total_count - start_character_index;
    const int count = character_count < 0 ? available : character_count;
    if (count < 0 || count > available) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }
    *required_units = static_cast<size_t>(count) + 1;
    if (!buffer || buffer_units < *required_units) {
      return PDFV_ERROR_BUFFER_TOO_SMALL;
    }
    const int written =
        FPDFText_GetText(text_page.get(), start_character_index, count, buffer);
    return written == count + 1 ? PDFV_OK : PDFV_ERROR_UNKNOWN;
  });
}

pdfv_status_t pdfv_search_text_utf16(pdfv_document_t* document,
                                     int32_t page_index,
                                     const uint16_t* query_utf16,
                                     uint32_t flags,
                                     pdfv_search_result_t** result) {
  return Guard([&] {
    if (RequireDocument(document) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    constexpr uint32_t kSupportedFlags =
        PDFV_SEARCH_MATCH_CASE | PDFV_SEARCH_MATCH_WHOLE_WORD |
        PDFV_SEARCH_CONSECUTIVE;
    if (!result) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }
    *result = nullptr;
    if (!query_utf16 || query_utf16[0] == 0 ||
        (flags & ~kSupportedFlags) != 0) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }

    ScopedPage page(FPDF_LoadPage(document->handle, page_index));
    if (!page.get()) {
      return PDFV_ERROR_PAGE;
    }
    ScopedTextPage text_page(FPDFText_LoadPage(page.get()));
    if (!text_page.get()) {
      return PDFV_ERROR_PAGE;
    }

    unsigned long pdfium_flags = 0;
    if (flags & PDFV_SEARCH_MATCH_CASE) {
      pdfium_flags |= FPDF_MATCHCASE;
    }
    if (flags & PDFV_SEARCH_MATCH_WHOLE_WORD) {
      pdfium_flags |= FPDF_MATCHWHOLEWORD;
    }
    if (flags & PDFV_SEARCH_CONSECUTIVE) {
      pdfium_flags |= FPDF_CONSECUTIVE;
    }
    ScopedSearch search(FPDFText_FindStart(
        text_page.get(), query_utf16, pdfium_flags, 0));
    if (!search.get()) {
      return PDFV_ERROR_UNKNOWN;
    }

    auto collected = std::make_unique<pdfv_search_result_t>();
    while (FPDFText_FindNext(search.get())) {
      const int start_character_index =
          FPDFText_GetSchResultIndex(search.get());
      const int character_count = FPDFText_GetSchCount(search.get());
      if (start_character_index < 0 || character_count <= 0) {
        return PDFV_ERROR_UNKNOWN;
      }

      const int rect_count = FPDFText_CountRects(
          text_page.get(), start_character_index, character_count);
      if (rect_count < 0) {
        return PDFV_ERROR_UNKNOWN;
      }
      pdfv_search_match_t match{};
      match.start_character_index = start_character_index;
      match.character_count = character_count;
      match.first_rect = collected->rects.size();
      match.rect_count = static_cast<size_t>(rect_count);
      collected->matches.push_back(match);

      for (int rect_index = 0; rect_index < rect_count; ++rect_index) {
        double left = 0;
        double top = 0;
        double right = 0;
        double bottom = 0;
        if (!FPDFText_GetRect(text_page.get(), rect_index, &left, &top,
                              &right, &bottom)) {
          return PDFV_ERROR_UNKNOWN;
        }
        collected->rects.push_back({left, bottom, right, top});
      }
    }

    g_search_results.insert(collected.get());
    *result = collected.release();
    return PDFV_OK;
  });
}

pdfv_status_t pdfv_get_search_result_counts(pdfv_search_result_t* result,
                                            size_t* match_count,
                                            size_t* rect_count) {
  return Guard([&] {
    if (RequireSearchResult(result) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    if (!match_count || !rect_count) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }
    *match_count = result->matches.size();
    *rect_count = result->rects.size();
    return PDFV_OK;
  });
}

pdfv_status_t pdfv_get_search_match(pdfv_search_result_t* result,
                                    size_t match_index,
                                    pdfv_search_match_t* match) {
  return Guard([&] {
    if (RequireSearchResult(result) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    if (!match || match_index >= result->matches.size()) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }
    *match = result->matches[match_index];
    return PDFV_OK;
  });
}

pdfv_status_t pdfv_get_search_rect(pdfv_search_result_t* result,
                                   size_t rect_index,
                                   pdfv_rect_t* rect) {
  return Guard([&] {
    if (RequireSearchResult(result) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    if (!rect || rect_index >= result->rects.size()) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }
    *rect = result->rects[rect_index];
    return PDFV_OK;
  });
}

pdfv_status_t pdfv_destroy_search_result(pdfv_search_result_t* result) {
  return Guard([&] {
    if (RequireSearchResult(result) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    g_search_results.erase(result);
    delete result;
    return PDFV_OK;
  });
}

pdfv_status_t pdfv_get_page_links(
    pdfv_document_t* document,
    int32_t page_index,
    pdfv_link_t* links,
    size_t link_capacity,
    pdfv_quad_t* quads,
    size_t quad_capacity,
    char* strings_utf8,
    size_t string_capacity,
    size_t* required_links,
    size_t* required_quads,
    size_t* required_string_bytes) {
  return Guard([&] {
    if (RequireDocument(document) != PDFV_OK) {
      return PDFV_ERROR_CLOSED;
    }
    if (!required_links || !required_quads || !required_string_bytes) {
      return PDFV_ERROR_INVALID_ARGUMENT;
    }

    ScopedPage page(FPDF_LoadPage(document->handle, page_index));
    if (!page.get()) {
      return PDFV_ERROR_PAGE;
    }
    std::vector<CollectedLink> collected;
    int position = 0;
    FPDF_LINK link = nullptr;
    while (FPDFLink_Enumerate(page.get(), &position, &link)) {
      if (!link) {
        return PDFV_ERROR_UNKNOWN;
      }
      collected.push_back(ReadLink(document->handle, link));
    }

    size_t total_quads = 0;
    size_t total_string_bytes = 0;
    for (const CollectedLink& item : collected) {
      if (item.quads.size() >
              std::numeric_limits<size_t>::max() - total_quads ||
          item.string.size() >
              std::numeric_limits<size_t>::max() - total_string_bytes) {
        return PDFV_ERROR_OUT_OF_MEMORY;
      }
      total_quads += item.quads.size();
      total_string_bytes += item.string.size();
    }
    *required_links = collected.size();
    *required_quads = total_quads;
    *required_string_bytes = total_string_bytes;

    if (collected.empty()) {
      return PDFV_OK;
    }
    if (!links || link_capacity < collected.size() ||
        (total_quads > 0 && (!quads || quad_capacity < total_quads)) ||
        (total_string_bytes > 0 &&
         (!strings_utf8 || string_capacity < total_string_bytes))) {
      return PDFV_ERROR_BUFFER_TOO_SMALL;
    }

    size_t quad_offset = 0;
    size_t string_offset = 0;
    for (size_t index = 0; index < collected.size(); ++index) {
      CollectedLink& item = collected[index];
      item.link.first_quad = quad_offset;
      item.link.quad_count = item.quads.size();
      item.link.string_offset = string_offset;
      item.link.string_length = item.string.size();
      links[index] = item.link;
      for (const pdfv_quad_t& quad : item.quads) {
        quads[quad_offset++] = quad;
      }
      for (char character : item.string) {
        strings_utf8[string_offset++] = character;
      }
    }
    return PDFV_OK;
  });
}
