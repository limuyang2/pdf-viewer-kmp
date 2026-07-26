#include <jni.h>

#include <cstdint>
#include <limits>
#include <memory>
#include <new>
#include <string>
#include <vector>

#include "fpdf_doc.h"
#include "fpdf_edit.h"
#include "fpdfview.h"

namespace {

constexpr jlong kOpenResultSize = 3;
constexpr jlong kPageInfoResultSize = 8;

struct Document {
  std::vector<uint8_t> bytes;
  FPDF_DOCUMENT handle = nullptr;

  ~Document() {
    if (handle) {
      FPDF_CloseDocument(handle);
    }
  }
};

struct Page {
  explicit Page(FPDF_PAGE value) : handle(value) {}
  ~Page() {
    if (handle) {
      FPDF_ClosePage(handle);
    }
  }
  FPDF_PAGE handle;
};

struct Bitmap {
  explicit Bitmap(FPDF_BITMAP value) : handle(value) {}
  ~Bitmap() {
    if (handle) {
      FPDFBitmap_Destroy(handle);
    }
  }
  FPDF_BITMAP handle;
};

Document* FromHandle(jlong handle) {
  return reinterpret_cast<Document*>(static_cast<intptr_t>(handle));
}

jlong ToHandle(Document* document) {
  return static_cast<jlong>(reinterpret_cast<intptr_t>(document));
}

std::string ToUtf8(JNIEnv* env, jstring value) {
  if (!value) {
    return {};
  }
  const jsize length = env->GetStringLength(value);
  const jchar* chars = env->GetStringChars(value, nullptr);
  if (!chars) {
    return {};
  }

  std::string result;
  result.reserve(static_cast<size_t>(length) * 3);
  for (jsize index = 0; index < length; ++index) {
    uint32_t code_point = chars[index];
    if (code_point >= 0xD800 && code_point <= 0xDBFF &&
        index + 1 < length) {
      const uint32_t low = chars[index + 1];
      if (low >= 0xDC00 && low <= 0xDFFF) {
        code_point =
            0x10000 + ((code_point - 0xD800) << 10) + (low - 0xDC00);
        ++index;
      }
    }

    if (code_point <= 0x7F) {
      result.push_back(static_cast<char>(code_point));
    } else if (code_point <= 0x7FF) {
      result.push_back(static_cast<char>(0xC0 | (code_point >> 6)));
      result.push_back(static_cast<char>(0x80 | (code_point & 0x3F)));
    } else if (code_point <= 0xFFFF) {
      result.push_back(static_cast<char>(0xE0 | (code_point >> 12)));
      result.push_back(
          static_cast<char>(0x80 | ((code_point >> 6) & 0x3F)));
      result.push_back(static_cast<char>(0x80 | (code_point & 0x3F)));
    } else {
      result.push_back(static_cast<char>(0xF0 | (code_point >> 18)));
      result.push_back(
          static_cast<char>(0x80 | ((code_point >> 12) & 0x3F)));
      result.push_back(
          static_cast<char>(0x80 | ((code_point >> 6) & 0x3F)));
      result.push_back(static_cast<char>(0x80 | (code_point & 0x3F)));
    }
  }
  env->ReleaseStringChars(value, chars);
  return result;
}

jlongArray NewOpenResult(JNIEnv* env,
                         Document* document,
                         jint page_count,
                         unsigned long error_code) {
  jlongArray result = env->NewLongArray(kOpenResultSize);
  if (!result) {
    return nullptr;
  }
  const jlong values[kOpenResultSize] = {
      document ? ToHandle(document) : 0,
      static_cast<jlong>(page_count),
      static_cast<jlong>(error_code),
  };
  env->SetLongArrayRegion(result, 0, kOpenResultSize, values);
  return result;
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativeInitialize(
    JNIEnv*,
    jobject) {
  FPDF_LIBRARY_CONFIG config{};
  config.version = 2;
  FPDF_InitLibraryWithConfig(&config);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativeDestroy(
    JNIEnv*,
    jobject) {
  FPDF_DestroyLibrary();
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativeOpen(
    JNIEnv* env,
    jobject,
    jbyteArray data,
    jstring password) {
  try {
    if (!data) {
      return NewOpenResult(env, nullptr, 0, FPDF_ERR_FORMAT);
    }
    const jsize size = env->GetArrayLength(data);
    if (size <= 0) {
      return NewOpenResult(env, nullptr, 0, FPDF_ERR_FORMAT);
    }

    auto document = std::make_unique<Document>();
    document->bytes.resize(static_cast<size_t>(size));
    env->GetByteArrayRegion(
        data,
        0,
        size,
        reinterpret_cast<jbyte*>(document->bytes.data()));
    if (env->ExceptionCheck()) {
      return nullptr;
    }

    const std::string utf8_password = ToUtf8(env, password);
    if (env->ExceptionCheck()) {
      return nullptr;
    }
    document->handle = FPDF_LoadMemDocument64(
        document->bytes.data(),
        document->bytes.size(),
        password ? utf8_password.c_str() : nullptr);
    if (!document->handle) {
      const unsigned long error = FPDF_GetLastError();
      return NewOpenResult(env, nullptr, 0, error);
    }

    const int page_count = FPDF_GetPageCount(document->handle);
    if (page_count < 0) {
      return NewOpenResult(env, nullptr, 0, FPDF_ERR_UNKNOWN);
    }
    Document* released = document.release();
    jlongArray result = NewOpenResult(env, released, page_count, 0);
    if (!result) {
      delete released;
    }
    return result;
  } catch (const std::bad_alloc&) {
    return NewOpenResult(env, nullptr, 0, FPDF_ERR_UNKNOWN);
  } catch (...) {
    return NewOpenResult(env, nullptr, 0, FPDF_ERR_UNKNOWN);
  }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativeClose(
    JNIEnv*,
    jobject,
    jlong handle) {
  delete FromHandle(handle);
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativePageInformation(
    JNIEnv* env,
    jobject,
    jlong handle,
    jint page_index) {
  Document* document = FromHandle(handle);
  if (!document || !document->handle) {
    return nullptr;
  }
  Page page(FPDF_LoadPage(document->handle, page_index));
  if (!page.handle) {
    return nullptr;
  }

  const double width = FPDF_GetPageWidthF(page.handle);
  const double height = FPDF_GetPageHeightF(page.handle);
  const int rotation = FPDFPage_GetRotation(page.handle);
  FS_RECTF bounds{};
  const bool has_bounds = FPDF_GetPageBoundingBox(page.handle, &bounds);

  jdoubleArray result = env->NewDoubleArray(kPageInfoResultSize);
  if (!result) {
    return nullptr;
  }
  const jdouble values[kPageInfoResultSize] = {
      width,
      height,
      static_cast<double>(rotation),
      bounds.left,
      bounds.bottom,
      bounds.right,
      bounds.top,
      has_bounds ? 1.0 : 0.0,
  };
  env->SetDoubleArrayRegion(result, 0, kPageInfoResultSize, values);
  return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativeRender(
    JNIEnv* env,
    jobject,
    jlong handle,
    jint page_index,
    jint width,
    jint height,
    jint rotation,
    jlong background_color,
    jboolean render_annotations,
    jboolean grayscale,
    jboolean lcd_text) {
  try {
    Document* document = FromHandle(handle);
    if (!document || !document->handle || width <= 0 || height <= 0) {
      return nullptr;
    }
    const int64_t stride = static_cast<int64_t>(width) * 4;
    const int64_t byte_count = stride * height;
    if (stride > std::numeric_limits<int>::max() ||
        byte_count > std::numeric_limits<jsize>::max()) {
      return nullptr;
    }

    Page page(FPDF_LoadPage(document->handle, page_index));
    if (!page.handle) {
      return nullptr;
    }
    std::vector<uint8_t> pixels(static_cast<size_t>(byte_count));
    Bitmap bitmap(FPDFBitmap_CreateEx(
        width,
        height,
        FPDFBitmap_BGRA,
        pixels.data(),
        static_cast<int>(stride)));
    if (!bitmap.handle) {
      return nullptr;
    }

    const bool filled = FPDFBitmap_FillRect(
        bitmap.handle,
        0,
        0,
        width,
        height,
        static_cast<FPDF_DWORD>(background_color));
    if (filled) {
      int flags = 0;
      if (render_annotations) {
        flags |= FPDF_ANNOT;
      }
      if (grayscale) {
        flags |= FPDF_GRAYSCALE;
      }
      if (lcd_text) {
        flags |= FPDF_LCD_TEXT;
      }
      FPDF_RenderPageBitmap(
          bitmap.handle,
          page.handle,
          0,
          0,
          width,
          height,
          rotation,
          flags);
    }
    if (!filled) {
      return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(byte_count));
    if (!result) {
      return nullptr;
    }
    env->SetByteArrayRegion(
        result,
        0,
        static_cast<jsize>(byte_count),
        reinterpret_cast<const jbyte*>(pixels.data()));
    return result;
  } catch (...) {
    return nullptr;
  }
}
