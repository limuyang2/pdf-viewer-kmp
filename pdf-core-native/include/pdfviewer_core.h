#ifndef PDF_VIEWER_NATIVE_CORE_PDFVIEWER_CORE_H_
#define PDF_VIEWER_NATIVE_CORE_PDFVIEWER_CORE_H_

#include <stddef.h>
#include <stdint.h>

#if defined(_WIN32)
#define PDFV_EXPORT __declspec(dllexport)
#else
#define PDFV_EXPORT __attribute__((visibility("default")))
#endif

#ifdef __cplusplus
extern "C" {
#endif

#define PDFV_ABI_VERSION 1u

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
  PDFV_ERROR_OUT_OF_MEMORY = 11,
  PDFV_ERROR_INVALID_STATE = 12,
} pdfv_status_t;

typedef enum pdfv_render_flags {
  PDFV_RENDER_ANNOTATIONS = 1u << 0,
  PDFV_RENDER_GRAYSCALE = 1u << 1,
  PDFV_RENDER_LCD_TEXT = 1u << 2,
} pdfv_render_flags_t;

typedef struct pdfv_rect {
  double left;
  double bottom;
  double right;
  double top;
} pdfv_rect_t;

typedef struct pdfv_page_info {
  double width;
  double height;
  int32_t rotation;
  int32_t has_bounding_box;
  pdfv_rect_t bounding_box;
} pdfv_page_info_t;

typedef struct pdfv_document_info {
  int32_t has_version;
  int32_t version;
  uint64_t permissions;
  int32_t security_revision;
  int32_t has_valid_cross_reference_table;
  int32_t is_linearized;
} pdfv_document_info_t;

typedef struct pdfv_render_request {
  int32_t width;
  int32_t height;
  int32_t rotation;
  uint32_t background_argb;
  uint32_t flags;
} pdfv_render_request_t;

PDFV_EXPORT uint32_t pdfv_get_abi_version(void);

PDFV_EXPORT pdfv_status_t pdfv_initialize(void);

PDFV_EXPORT pdfv_status_t pdfv_destroy(void);

PDFV_EXPORT pdfv_status_t pdfv_open_memory(const uint8_t* data,
                                           size_t size,
                                           const char* password_utf8,
                                           pdfv_document_t** document,
                                           int32_t* page_count,
                                           uint32_t* pdfium_error);

PDFV_EXPORT pdfv_status_t pdfv_close_document(pdfv_document_t* document);

PDFV_EXPORT pdfv_status_t pdfv_get_document_info(
    pdfv_document_t* document,
    pdfv_document_info_t* info);

// UTF-16 buffer sizes include the trailing NUL code unit.
PDFV_EXPORT pdfv_status_t pdfv_get_metadata_utf16(
    pdfv_document_t* document,
    const char* tag,
    uint16_t* buffer,
    size_t buffer_units,
    size_t* required_units);

PDFV_EXPORT pdfv_status_t pdfv_get_page_label_utf16(
    pdfv_document_t* document,
    int32_t page_index,
    uint16_t* buffer,
    size_t buffer_units,
    size_t* required_units);

PDFV_EXPORT pdfv_status_t pdfv_get_page_info(pdfv_document_t* document,
                                             int32_t page_index,
                                             pdfv_page_info_t* info);

PDFV_EXPORT pdfv_status_t pdfv_render_page(
    pdfv_document_t* document,
    int32_t page_index,
    const pdfv_render_request_t* request,
    uint8_t* pixels,
    size_t pixels_size);

// A negative character_count extracts through the end of the page.
// UTF-16 buffer sizes include the trailing NUL code unit.
PDFV_EXPORT pdfv_status_t pdfv_extract_text_utf16(
    pdfv_document_t* document,
    int32_t page_index,
    int32_t start_character_index,
    int32_t character_count,
    uint16_t* buffer,
    size_t buffer_units,
    size_t* required_units);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // PDF_VIEWER_NATIVE_CORE_PDFVIEWER_CORE_H_
