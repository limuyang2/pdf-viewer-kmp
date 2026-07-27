#include <jni.h>

#include <cstdint>
#include <limits>
#include <string>
#include <vector>

#include "pdfviewer_core.h"

namespace {

constexpr jsize kOpenResultSize = 4;
constexpr jsize kPageInfoResultSize = 8;
constexpr jsize kDocumentInfoResultSize = 7;

pdfv_document_t* FromHandle(jlong handle) {
  return reinterpret_cast<pdfv_document_t*>(static_cast<intptr_t>(handle));
}

jlong ToHandle(pdfv_document_t* document) {
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
                         pdfv_document_t* document,
                         jint page_count,
                         uint32_t pdfium_error,
                         pdfv_status_t status) {
  jlongArray result = env->NewLongArray(kOpenResultSize);
  if (!result) {
    return nullptr;
  }
  const jlong values[kOpenResultSize] = {
      document ? ToHandle(document) : 0,
      static_cast<jlong>(page_count),
      static_cast<jlong>(pdfium_error),
      static_cast<jlong>(status),
  };
  env->SetLongArrayRegion(result, 0, kOpenResultSize, values);
  return result;
}

template <typename Read>
jstring ReadUtf16(JNIEnv* env, Read read) {
  try {
    size_t required_units = 0;
    const pdfv_status_t size_status =
        read(nullptr, 0, &required_units);
    if (size_status == PDFV_OK && required_units == 0) {
      return nullptr;
    }
    if (size_status != PDFV_ERROR_BUFFER_TOO_SMALL ||
        required_units == 0 ||
        required_units >
            static_cast<size_t>(std::numeric_limits<jsize>::max())) {
      return nullptr;
    }

    std::vector<uint16_t> utf16(required_units);
    if (read(utf16.data(), utf16.size(), &required_units) != PDFV_OK ||
        required_units == 0) {
      return nullptr;
    }
    return env->NewString(
        reinterpret_cast<const jchar*>(utf16.data()),
        static_cast<jsize>(required_units - 1));
  } catch (...) {
    return nullptr;
  }
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativeInitialize(
    JNIEnv*,
    jobject) {
  pdfv_initialize();
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativeDestroy(
    JNIEnv*,
    jobject) {
  pdfv_destroy();
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativeOpen(
    JNIEnv* env,
    jobject,
    jbyteArray data,
    jstring password) {
  try {
    if (!data) {
      return NewOpenResult(
          env, nullptr, 0, 0, PDFV_ERROR_INVALID_ARGUMENT);
    }
    const jsize size = env->GetArrayLength(data);
    if (size <= 0) {
      return NewOpenResult(env, nullptr, 0, 0, PDFV_ERROR_FORMAT);
    }
    const std::string utf8_password = ToUtf8(env, password);
    if (env->ExceptionCheck()) {
      return nullptr;
    }
    jbyte* bytes = env->GetByteArrayElements(data, nullptr);
    if (!bytes) {
      return nullptr;
    }

    pdfv_document_t* document = nullptr;
    int32_t page_count = 0;
    uint32_t pdfium_error = 0;
    const pdfv_status_t status =
        pdfv_open_memory(reinterpret_cast<const uint8_t*>(bytes),
                         static_cast<size_t>(size),
                         password ? utf8_password.c_str() : nullptr,
                         &document,
                         &page_count,
                         &pdfium_error);
    env->ReleaseByteArrayElements(data, bytes, JNI_ABORT);

    jlongArray result =
        NewOpenResult(env, document, page_count, pdfium_error, status);
    if (!result && document) {
      pdfv_close_document(document);
    }
    return result;
  } catch (...) {
    return nullptr;
  }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativeClose(
    JNIEnv*,
    jobject,
    jlong handle) {
  pdfv_close_document(FromHandle(handle));
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativeDocumentInformation(
    JNIEnv* env,
    jobject,
    jlong handle) {
  pdfv_document_info_t info{};
  const pdfv_status_t status =
      pdfv_get_document_info(FromHandle(handle), &info);
  jlongArray result = env->NewLongArray(kDocumentInfoResultSize);
  if (!result) {
    return nullptr;
  }
  const jlong values[kDocumentInfoResultSize] = {
      static_cast<jlong>(status),
      info.has_version,
      info.version,
      static_cast<jlong>(info.permissions),
      info.security_revision,
      info.has_valid_cross_reference_table,
      info.is_linearized,
  };
  env->SetLongArrayRegion(result, 0, kDocumentInfoResultSize, values);
  return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativeMetadata(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring tag) {
  try {
    const std::string utf8_tag = ToUtf8(env, tag);
    if (env->ExceptionCheck() || utf8_tag.empty()) {
      return nullptr;
    }
    return ReadUtf16(env, [&](uint16_t* buffer,
                              size_t units,
                              size_t* required) {
      return pdfv_get_metadata_utf16(
          FromHandle(handle), utf8_tag.c_str(), buffer, units, required);
    });
  } catch (...) {
    return nullptr;
  }
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativePageLabel(
    JNIEnv* env,
    jobject,
    jlong handle,
    jint page_index) {
  return ReadUtf16(env, [&](uint16_t* buffer,
                            size_t units,
                            size_t* required) {
    return pdfv_get_page_label_utf16(
        FromHandle(handle), page_index, buffer, units, required);
  });
}

extern "C" JNIEXPORT jdoubleArray JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativePageInformation(
    JNIEnv* env,
    jobject,
    jlong handle,
    jint page_index) {
  pdfv_page_info_t info{};
  if (pdfv_get_page_info(FromHandle(handle), page_index, &info) != PDFV_OK) {
    return nullptr;
  }
  jdoubleArray result = env->NewDoubleArray(kPageInfoResultSize);
  if (!result) {
    return nullptr;
  }
  const jdouble values[kPageInfoResultSize] = {
      info.width,
      info.height,
      static_cast<double>(info.rotation),
      info.bounding_box.left,
      info.bounding_box.bottom,
      info.bounding_box.right,
      info.bounding_box.top,
      info.has_bounding_box ? 1.0 : 0.0,
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
    if (width <= 0 || height <= 0) {
      return nullptr;
    }
    const int64_t byte_count =
        static_cast<int64_t>(width) * height * 4;
    if (byte_count <= 0 ||
        byte_count > std::numeric_limits<jsize>::max()) {
      return nullptr;
    }

    std::vector<uint8_t> pixels(static_cast<size_t>(byte_count));
    pdfv_render_request_t request{};
    request.width = width;
    request.height = height;
    request.rotation = rotation;
    request.background_argb = static_cast<uint32_t>(background_color);
    if (render_annotations) {
      request.flags |= PDFV_RENDER_ANNOTATIONS;
    }
    if (grayscale) {
      request.flags |= PDFV_RENDER_GRAYSCALE;
    }
    if (lcd_text) {
      request.flags |= PDFV_RENDER_LCD_TEXT;
    }
    if (pdfv_render_page(FromHandle(handle), page_index, &request,
                         pixels.data(), pixels.size()) != PDFV_OK) {
      return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(byte_count));
    if (!result) {
      return nullptr;
    }
    env->SetByteArrayRegion(
        result, 0, static_cast<jsize>(byte_count),
        reinterpret_cast<const jbyte*>(pixels.data()));
    return result;
  } catch (...) {
    return nullptr;
  }
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_limuyang2_pdf_viewer_internal_AndroidPdfiumNative_nativeExtractText(
    JNIEnv* env,
    jobject,
    jlong handle,
    jint page_index,
    jint start_character_index,
    jint character_count) {
  return ReadUtf16(env, [&](uint16_t* buffer,
                            size_t units,
                            size_t* required) {
    return pdfv_extract_text_utf16(
        FromHandle(handle), page_index, start_character_index,
        character_count, buffer, units, required);
  });
}
