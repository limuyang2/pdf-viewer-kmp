#!/bin/sh

set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
web_pdfium_dir="$project_root/pdf-core/src/webMain/resources/pdfium"
jvm_manifest="$project_root/pdf-core/src/jvmMain/resources/pdfium/manifest.properties"
web_manifest="$web_pdfium_dir/manifest.properties"

grep -q '__pdfViewerCreatePdfiumModule' "$web_pdfium_dir/pdfium.js"
test -f "$web_pdfium_dir/pdfium-adapter.js"
cmp "$jvm_manifest" "$web_manifest"
! grep -q 'globalThis.Module' "$web_pdfium_dir/pdfium.js"

wasm_digest=$(shasum -a 256 "$web_pdfium_dir/pdfium.wasm" | awk '{print $1}')
manifest_digest=$(sed -n 's/^asset.wasm.sha256=//p' "$web_manifest")

test "$wasm_digest" = "$manifest_digest"
