#!/bin/sh

set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
web_pdfium_dir="$project_root/pdf-core/src/webMain/resources/pdfium"
jvm_manifest="$project_root/pdf-core/src/jvmMain/resources/pdfium/manifest.properties"
web_manifest="$web_pdfium_dir/manifest.properties"
ios_cinterop_dir="$project_root/pdf-core/src/nativeInterop/cinterop"

grep -q '__pdfViewerCreatePdfiumModule' "$web_pdfium_dir/pdfium.js"
test -f "$web_pdfium_dir/pdfium-adapter.js"
cmp "$jvm_manifest" "$web_manifest"
! grep -q 'globalThis.Module' "$web_pdfium_dir/pdfium.js"

wasm_digest=$(shasum -a 256 "$web_pdfium_dir/pdfium.wasm" | awk '{print $1}')
manifest_digest=$(sed -n 's/^asset.wasm.sha256=//p' "$web_manifest")

test "$wasm_digest" = "$manifest_digest"

verify_runtime_digest() {
    classifier=$1
    library=$2
    expected=$(sed -n "s/^runtime\\.$classifier\\.sha256=//p" "$jvm_manifest")
    actual=$(shasum -a 256 "$library" | awk '{print $1}')
    test -n "$expected"
    test "$actual" = "$expected"
}

verify_runtime_digest mac-arm64 \
    "$project_root/pdf-core/src/jvmMain/resources/pdfium/darwin-aarch64/libpdfium.dylib"
verify_runtime_digest mac-x64 \
    "$project_root/pdf-core/src/jvmMain/resources/pdfium/darwin-x86-64/libpdfium.dylib"
verify_runtime_digest linux-x64 \
    "$project_root/pdf-core/src/jvmMain/resources/pdfium/linux-x86-64/libpdfium.so"
verify_runtime_digest win-x64 \
    "$project_root/pdf-core/src/jvmMain/resources/pdfium/win32-x86-64/pdfium.dll"

test -f \
    "$ios_cinterop_dir/lib/iosArm64/libpdfium.a"
test -f \
    "$ios_cinterop_dir/lib/iosSimulatorArm64/libpdfium.a"
grep -q \
    '^staticLibraries = libpdfviewer_core\.a libpdfium\.a$' \
    "$ios_cinterop_dir/pdfviewerCore.def"
grep -q \
    '^linkerOpts = -lc++ -framework CoreFoundation -framework CoreGraphics$' \
    "$ios_cinterop_dir/pdfviewerCore.def"
