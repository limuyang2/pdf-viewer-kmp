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

#define PDFV_ABI_VERSION 3u

typedef struct pdfv_document pdfv_document_t;
typedef struct pdfv_search_result pdfv_search_result_t;

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

typedef enum pdfv_search_flags {
  PDFV_SEARCH_MATCH_CASE = 1u << 0,
  PDFV_SEARCH_MATCH_WHOLE_WORD = 1u << 1,
  PDFV_SEARCH_CONSECUTIVE = 1u << 2,
} pdfv_search_flags_t;

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

typedef enum pdfv_link_target_type {
  PDFV_LINK_TARGET_INTERNAL = 1,
  PDFV_LINK_TARGET_URI = 2,
  PDFV_LINK_TARGET_REMOTE_DOCUMENT = 3,
  PDFV_LINK_TARGET_UNSUPPORTED = 4,
} pdfv_link_target_type_t;

typedef struct pdfv_quad {
  double x1;
  double y1;
  double x2;
  double y2;
  double x3;
  double y3;
  double x4;
  double y4;
} pdfv_quad_t;

typedef struct pdfv_destination {
  int32_t page_index;
  uint32_t view_mode;
  uint32_t parameter_count;
  double parameters[4];
  int32_t has_x;
  int32_t has_y;
  int32_t has_zoom;
  double x;
  double y;
  double zoom;
} pdfv_destination_t;

typedef struct pdfv_link {
  uint32_t target_type;
  uint32_t native_action_type;
  size_t first_quad;
  size_t quad_count;
  pdfv_destination_t destination;
  size_t string_offset;
  size_t string_length;
} pdfv_link_t;

typedef struct pdfv_search_match {
  int32_t start_character_index;
  int32_t character_count;
  size_t first_rect;
  size_t rect_count;
} pdfv_search_match_t;

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

// The query must be a non-empty, NUL-terminated UTF-16 string.
// The returned result owns its matches and rectangles independently of the
// document and must be released with pdfv_destroy_search_result().
PDFV_EXPORT pdfv_status_t pdfv_search_text_utf16(
    pdfv_document_t* document,
    int32_t page_index,
    const uint16_t* query_utf16,
    uint32_t flags,
    pdfv_search_result_t** result);

PDFV_EXPORT pdfv_status_t pdfv_get_search_result_counts(
    pdfv_search_result_t* result,
    size_t* match_count,
    size_t* rect_count);

PDFV_EXPORT pdfv_status_t pdfv_get_search_match(
    pdfv_search_result_t* result,
    size_t match_index,
    pdfv_search_match_t* match);

PDFV_EXPORT pdfv_status_t pdfv_get_search_rect(
    pdfv_search_result_t* result,
    size_t rect_index,
    pdfv_rect_t* rect);

PDFV_EXPORT pdfv_status_t pdfv_destroy_search_result(
    pdfv_search_result_t* result);

// Call once with null output buffers to obtain required element counts, then
// call again with buffers of at least those sizes. UTF-8 strings are packed
// without trailing NUL bytes and referenced by each link's offset and length.
PDFV_EXPORT pdfv_status_t pdfv_get_page_links(
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
    size_t* required_string_bytes);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // PDF_VIEWER_NATIVE_CORE_PDFVIEWER_CORE_H_
