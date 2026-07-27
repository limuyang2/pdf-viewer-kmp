#!/bin/sh

set -eu

project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
module_dir="$project_root/pdf-core"
android_native_module_dir="$project_root/pdf-core-android-native"
version=${1:-chromium/7961}

if ! printf '%s\n' "$version" | grep -Eq '^chromium/[0-9]+$'; then
    echo "Usage: $0 chromium/<build>" >&2
    exit 2
fi

encoded_version=$(printf '%s' "$version" | sed 's|/|%2F|g')
release_base="https://github.com/bblanchon/pdfium-binaries/releases/download/$encoded_version"
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/pdfium-update.XXXXXX")
download_dir="$work_dir/downloads"
stage_dir="$work_dir/stage"
manifest="$stage_dir/pdfium/manifest.properties"
runtime_manifest="$stage_dir/runtime-manifest.properties"
web_glue="$work_dir/pdfium.js"

cleanup() {
    rm -rf "$work_dir"
}
trap cleanup EXIT INT TERM

mkdir -p "$download_dir" "$stage_dir/pdfium"

download() {
    classifier=$1
    archive="$download_dir/pdfium-$classifier.tgz"
    url="$release_base/pdfium-$classifier.tgz"

    echo "Downloading pdfium-$classifier.tgz"
    curl \
        --fail \
        --location \
        --retry 3 \
        --retry-all-errors \
        --connect-timeout 20 \
        --output "$archive.part" \
        "$url"
    mv "$archive.part" "$archive"

    digest=$(shasum -a 256 "$archive" | awk '{print $1}')
    printf 'asset.%s.sha256=%s\n' "$classifier" "$digest" >> "$manifest"
}

extract_file() {
    classifier=$1
    entry=$2
    output=$3
    archive="$download_dir/pdfium-$classifier.tgz"

    mkdir -p "$(dirname "$output")"
    tar -xOzf "$archive" "$entry" > "$output"
}

for classifier in \
    android-arm \
    android-arm64 \
    android-x86 \
    android-x64 \
    ios-device-arm64 \
    ios-simulator-arm64 \
    mac-arm64 \
    mac-x64 \
    win-x64 \
    linux-x64 \
    wasm
do
    download "$classifier"
done

printf 'version=%s\n' "$version" > "$manifest.tmp"
printf 'flavor=pdfium\n' >> "$manifest.tmp"
printf 'pdfJavaScript=false\n' >> "$manifest.tmp"
printf 'xfa=false\n' >> "$manifest.tmp"
cat "$manifest" >> "$manifest.tmp"
mv "$manifest.tmp" "$manifest"

extract_file android-arm lib/libpdfium.so \
    "$stage_dir/android/armeabi-v7a/libpdfium.so"
extract_file android-arm64 lib/libpdfium.so \
    "$stage_dir/android/arm64-v8a/libpdfium.so"
extract_file android-x86 lib/libpdfium.so \
    "$stage_dir/android/x86/libpdfium.so"
extract_file android-x64 lib/libpdfium.so \
    "$stage_dir/android/x86_64/libpdfium.so"

extract_file ios-device-arm64 lib/libpdfium.dylib \
    "$stage_dir/nativeInterop/cinterop/lib/iosArm64/libpdfium.dylib"
extract_file ios-simulator-arm64 lib/libpdfium.dylib \
    "$stage_dir/nativeInterop/cinterop/lib/iosSimulatorArm64/libpdfium.dylib"

if ! command -v install_name_tool >/dev/null 2>&1; then
    echo "install_name_tool is required to prepare the iOS PDFium binaries" >&2
    exit 1
fi

install_name_tool -id @rpath/libpdfium.dylib \
    "$stage_dir/nativeInterop/cinterop/lib/iosArm64/libpdfium.dylib"
install_name_tool -id @rpath/libpdfium.dylib \
    "$stage_dir/nativeInterop/cinterop/lib/iosSimulatorArm64/libpdfium.dylib"

extract_file mac-arm64 lib/libpdfium.dylib \
    "$stage_dir/jvm/pdfium/darwin-aarch64/libpdfium.dylib"
extract_file mac-x64 lib/libpdfium.dylib \
    "$stage_dir/jvm/pdfium/darwin-x86-64/libpdfium.dylib"
extract_file linux-x64 lib/libpdfium.so \
    "$stage_dir/jvm/pdfium/linux-x86-64/libpdfium.so"
extract_file win-x64 bin/pdfium.dll \
    "$stage_dir/jvm/pdfium/win32-x86-64/pdfium.dll"

extract_file wasm lib/pdfium.js "$web_glue"
extract_file wasm lib/pdfium.wasm "$stage_dir/web/pdfium/pdfium.wasm"

sed 's/^asset\.wasm\.sha256=/asset.wasm.archive.sha256=/' \
    "$manifest" > "$runtime_manifest"
wasm_digest=$(shasum -a 256 "$stage_dir/web/pdfium/pdfium.wasm" | awk '{print $1}')
printf 'asset.wasm.sha256=%s\n' "$wasm_digest" >> "$runtime_manifest"
cat \
    "$project_root/scripts/pdfium-web/pdfium-module-prefix.js" \
    "$web_glue" \
    "$project_root/scripts/pdfium-web/pdfium-module-suffix.js" \
    > "$stage_dir/web/pdfium/pdfium.js"
cp "$project_root/scripts/pdfium-web/pdfium-adapter.js" \
    "$stage_dir/web/pdfium/pdfium-adapter.js"
mkdir -p "$stage_dir/jvm/pdfium"
cp "$runtime_manifest" "$stage_dir/jvm/pdfium/manifest.properties"
cp "$runtime_manifest" "$stage_dir/web/pdfium/manifest.properties"

mkdir -p "$stage_dir/nativeInterop/cinterop"
tar -xzf "$download_dir/pdfium-android-arm64.tgz" \
    -C "$stage_dir/nativeInterop/cinterop" \
    include
rm -f "$stage_dir/nativeInterop/cinterop/include/fpdfview.h.orig"

mkdir -p "$stage_dir/pdfium/licenses"
tar -xzf "$download_dir/pdfium-android-arm64.tgz" \
    -C "$stage_dir/pdfium" \
    LICENSE \
    VERSION \
    args.gn \
    licenses

replace_directory() {
    source=$1
    target=$2
    parent=$(dirname "$target")

    mkdir -p "$parent"
    rm -rf "$target"
    mv "$source" "$target"
}

replace_directory \
    "$stage_dir/android" \
    "$android_native_module_dir/src/main/jniLibs"
replace_directory "$stage_dir/jvm" "$module_dir/src/jvmMain/resources"
replace_directory "$stage_dir/web" "$module_dir/src/webMain/resources"
replace_directory \
    "$stage_dir/nativeInterop/cinterop/include" \
    "$module_dir/src/nativeInterop/cinterop/include"
replace_directory \
    "$stage_dir/nativeInterop/cinterop/lib" \
    "$module_dir/src/nativeInterop/cinterop/lib"
replace_directory "$stage_dir/pdfium" "$module_dir/pdfium"

echo "PDFium $version installed into $module_dir"
