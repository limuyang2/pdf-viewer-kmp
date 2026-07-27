#include "pdfviewer_core.h"

#include <limits>
#include <memory>
#include <new>
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

namespace {

bool g_initialized = false;
std::unordered_set<pdfv_document_t*> g_documents;

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

bool FitsUtf16ByteCount(size_t units) {
  return units <= std::numeric_limits<unsigned long>::max() / 2;
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
    if (!g_documents.empty()) {
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
