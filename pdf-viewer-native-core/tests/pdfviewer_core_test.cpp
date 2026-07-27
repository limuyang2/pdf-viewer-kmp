#include "pdfviewer_core.h"

#include <cstdio>
#include <cstdint>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>

namespace {

int Fail(const char* message) {
  std::fprintf(stderr, "%s\n", message);
  return 1;
}

}  // namespace

int main(int argc, char** argv) {
  if (argc != 2) {
    return Fail("usage: pdfviewer_core_test <fixture.pdf>");
  }

  std::ifstream input(argv[1], std::ios::binary);
  const std::vector<uint8_t> bytes{
      std::istreambuf_iterator<char>(input),
      std::istreambuf_iterator<char>()};
  if (bytes.empty()) {
    return Fail("fixture is empty");
  }

  if (pdfv_get_abi_version() != PDFV_ABI_VERSION) {
    return Fail("ABI version mismatch");
  }
  if (pdfv_initialize() != PDFV_OK) {
    return Fail("PDFium initialization failed");
  }

  pdfv_document_t* document = nullptr;
  int32_t page_count = 0;
  uint32_t pdfium_error = 0;
  const pdfv_status_t open_status =
      pdfv_open_memory(bytes.data(),
                       bytes.size(),
                       nullptr,
                       &document,
                       &page_count,
                       &pdfium_error);
  if (open_status != PDFV_OK || !document || page_count <= 0 ||
      pdfium_error != 0) {
    pdfv_destroy();
    return Fail("opening fixture failed");
  }

  pdfv_document_info_t document_info{};
  if (pdfv_get_document_info(document, &document_info) != PDFV_OK ||
      !document_info.has_version || document_info.version <= 0) {
    pdfv_close_document(document);
    pdfv_destroy();
    return Fail("document information failed");
  }

  pdfv_page_info_t page_info{};
  if (pdfv_get_page_info(document, 0, &page_info) != PDFV_OK ||
      page_info.width <= 0 || page_info.height <= 0 ||
      page_info.rotation < 0 || page_info.rotation > 3) {
    pdfv_close_document(document);
    pdfv_destroy();
    return Fail("page information failed");
  }

  size_t text_units = 0;
  if (pdfv_extract_text_utf16(
          document, 0, 0, -1, nullptr, 0, &text_units) !=
          PDFV_ERROR_BUFFER_TOO_SMALL ||
      text_units <= 1) {
    pdfv_close_document(document);
    pdfv_destroy();
    return Fail("text size query failed");
  }
  std::vector<uint16_t> text(text_units);
  if (pdfv_extract_text_utf16(document,
                              0,
                              0,
                              -1,
                              text.data(),
                              text.size(),
                              &text_units) != PDFV_OK) {
    pdfv_close_document(document);
    pdfv_destroy();
    return Fail("text extraction failed");
  }

  pdfv_render_request_t request{};
  request.width = 32;
  request.height = 32;
  request.rotation = 0;
  request.background_argb = 0xFFFFFFFFu;
  request.flags = PDFV_RENDER_ANNOTATIONS;
  std::vector<uint8_t> pixels(
      static_cast<size_t>(request.width) * request.height * 4);
  if (pdfv_render_page(
          document, 0, &request, pixels.data(), pixels.size()) != PDFV_OK) {
    pdfv_close_document(document);
    pdfv_destroy();
    return Fail("page rendering failed");
  }

  pdfv_close_document(document);
  if (pdfv_destroy() != PDFV_OK) {
    return Fail("PDFium destruction failed");
  }
  return 0;
}
