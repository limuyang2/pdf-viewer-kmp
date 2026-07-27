cmake_minimum_required(VERSION 3.22.1)
project(pdfviewer_bridge LANGUAGES CXX)

add_library(pdfium SHARED IMPORTED)
set_target_properties(
    pdfium
    PROPERTIES
    IMPORTED_LOCATION
        "${CMAKE_CURRENT_LIST_DIR}/../jniLibs/${ANDROID_ABI}/libpdfium.so"
)

add_library(
    pdfviewer_bridge
    SHARED
    pdfviewer_bridge.cpp
    "${CMAKE_CURRENT_LIST_DIR}/../../../../pdf-core-native/src/pdfviewer_core.cpp"
)

target_include_directories(
    pdfviewer_bridge
    PRIVATE
    "${CMAKE_CURRENT_LIST_DIR}/../../../../pdf-core/src/nativeInterop/cinterop/include"
    "${CMAKE_CURRENT_LIST_DIR}/../../../../pdf-core-native/include"
)

target_compile_features(pdfviewer_bridge PRIVATE cxx_std_17)
target_compile_options(
    pdfviewer_bridge
    PRIVATE
    -Wall
    -Wextra
    -Werror
)
target_link_libraries(pdfviewer_bridge PRIVATE pdfium)
