cmake_minimum_required(VERSION 3.22.1)
project(pdfviewer_core LANGUAGES CXX)

set(PDFVIEWER_PDFIUM_INCLUDE_DIR "" CACHE PATH "PDFium public header directory")
set(PDFVIEWER_PDFIUM_LIBRARY "" CACHE FILEPATH "PDFium library")

add_library(pdfviewer_core STATIC src/pdfviewer_core.cpp)
target_include_directories(
    pdfviewer_core
    PUBLIC "${CMAKE_CURRENT_LIST_DIR}/include"
    PRIVATE "${PDFVIEWER_PDFIUM_INCLUDE_DIR}"
)
target_compile_features(pdfviewer_core PRIVATE cxx_std_17)
target_compile_options(pdfviewer_core PRIVATE -Wall -Wextra -Werror)

if(PDFVIEWER_PDFIUM_LIBRARY)
    add_library(pdfium SHARED IMPORTED)
    set_target_properties(
        pdfium
        PROPERTIES IMPORTED_LOCATION "${PDFVIEWER_PDFIUM_LIBRARY}"
    )
    target_link_libraries(pdfviewer_core PUBLIC pdfium)
endif()

option(PDFVIEWER_BUILD_TESTS "Build native core tests" OFF)
if(PDFVIEWER_BUILD_TESTS)
    enable_testing()
    add_executable(pdfviewer_core_test tests/pdfviewer_core_test.cpp)
    target_link_libraries(pdfviewer_core_test PRIVATE pdfviewer_core)
    add_test(
        NAME pdfviewer_core_test
        COMMAND pdfviewer_core_test "${PDFVIEWER_TEST_PDF}"
    )
    get_filename_component(
        PDFVIEWER_PDFIUM_LIBRARY_DIR
        "${PDFVIEWER_PDFIUM_LIBRARY}"
        DIRECTORY
    )
    set_tests_properties(
        pdfviewer_core_test
        PROPERTIES
        ENVIRONMENT "DYLD_LIBRARY_PATH=${PDFVIEWER_PDFIUM_LIBRARY_DIR}"
    )
endif()
