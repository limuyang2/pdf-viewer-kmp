# Build and PDFium binaries

This document is intended for project maintainers. Library users do not need
to download or build PDFium manually.

## Binary provenance

The project uses PDFium binaries and build tooling from:

<https://github.com/bblanchon/pdfium-binaries>

The currently pinned PDFium release is:

```text
chromium/7961
```

The selected flavor is `pdfium`, with PDF JavaScript and XFA disabled.
Android, JVM, and browser binaries are extracted from the upstream GitHub
Release archives:

```text
https://github.com/bblanchon/pdfium-binaries/releases/download/<version>/
```

The upstream iOS release archives contain only `libpdfium.dylib`. The iOS
artifacts in this repository are instead built as static `libpdfium.a`
archives from the same PDFium revision. Static archives are required so the
published Kotlin/Native cinterop KLIBs are self-contained for downstream
consumers.

The pinned version, flavor, feature flags, archive checksums, and runtime
checksums are recorded in both runtime manifests:

```text
pdf-core/src/jvmMain/resources/pdfium/manifest.properties
pdf-core/src/webMain/resources/pdfium/manifest.properties
```

## Included targets

| Target | Source | Repository destination |
| --- | --- | --- |
| Android armeabi-v7a | `android-arm` | `pdf-core-android-native/src/main/jniLibs/armeabi-v7a/` |
| Android arm64-v8a | `android-arm64` | `pdf-core-android-native/src/main/jniLibs/arm64-v8a/` |
| Android x86 | `android-x86` | `pdf-core-android-native/src/main/jniLibs/x86/` |
| Android x86_64 | `android-x64` | `pdf-core-android-native/src/main/jniLibs/x86_64/` |
| iOS device arm64 | static source build | `pdf-core/src/nativeInterop/cinterop/lib/iosArm64/libpdfium.a` |
| iOS simulator arm64 | static source build | `pdf-core/src/nativeInterop/cinterop/lib/iosSimulatorArm64/libpdfium.a` |
| JVM macOS arm64 | `mac-arm64` | `pdf-core/src/jvmMain/resources/pdfium/darwin-aarch64/` |
| JVM macOS x64 | `mac-x64` | `pdf-core/src/jvmMain/resources/pdfium/darwin-x86-64/` |
| JVM Linux x64 | `linux-x64` | `pdf-core/src/jvmMain/resources/pdfium/linux-x86-64/` |
| JVM Windows x64 | `win-x64` | `pdf-core/src/jvmMain/resources/pdfium/win32-x86-64/` |
| Browser Wasm | `wasm` | `pdf-core/src/webMain/resources/pdfium/` |

PDFium C headers are extracted from the `android-arm64` archive into:

```text
pdf-core/src/nativeInterop/cinterop/include
```

## Native bridge and iOS linkage

The project compiles its own thin native bridge:

- Android builds `pdf-core-android-native/src/main/cpp/pdfviewer_bridge.cpp`
  with CMake and links it to `libpdfium.so`.
- iOS builds `pdf-core-native/src/pdfviewer_core.cpp` once for each supported
  Apple target and archives it as `libpdfviewer_core.a`.

For iOS, the cinterop definition embeds both static archives:

```text
staticLibraries = libpdfviewer_core.a libpdfium.a
```

It also propagates the required libc++, CoreFoundation, and CoreGraphics
linker options. A published iOS cinterop KLIB therefore contains all PDFium
machine code needed by an external Kotlin Multiplatform project. Applications
must not add a `libpdfium.dylib` copy or code-sign phase.

The browser adapter files in `scripts/pdfium-web` are maintained by this
project. The updater combines them with the upstream `pdfium.js` and
`pdfium.wasm`.

## Building static PDFium for iOS

Build the device and simulator archives from the same PDFium revision used by
the other platforms. Use the experimental static build mode from
`bblanchon/pdfium-binaries` and configure these GN arguments:

```text
is_debug = false
pdf_is_complete_lib = true
pdf_enable_v8 = false
pdf_enable_xfa = false
symbol_level = 0
use_custom_libcxx = false
ios_deployment_target = "14.0"
```

The important constraints are:

- `pdf_is_complete_lib = true` creates a complete static archive;
- `use_custom_libcxx = false` uses the Apple system libc++ ABI instead of
  Chromium's private `std::__Cr` ABI;
- `symbol_level = 0` keeps each archive small enough for source control and
  Maven publication;
- device and simulator builds must use separate output directories;
- the device archive must contain the `IOS` platform and the simulator
  archive must contain the `IOSSIMULATOR` platform.

Build the two upstream targets as:

```bash
./build.sh -b chromium/7961 -s ios arm64 device
./build.sh -b chromium/7961 -s ios arm64 simulator
```

Apply the GN arguments above in the upstream configure step before building.
The resulting complete archive is normally staged as:

```text
staging/lib/libpdfium.a
```

Save the device archive before starting the simulator build because the
upstream staging directory is reused. Arrange the final files as:

```text
<static-root>/iosArm64/libpdfium.a
<static-root>/iosSimulatorArm64/libpdfium.a
```

Downloads needed by the Chromium build may use the local proxy:

```bash
export https_proxy=http://127.0.0.1:7890
export http_proxy=http://127.0.0.1:7890
export all_proxy=socks5://127.0.0.1:7891
```

Before installation, verify representative PDFium symbols and the platform of
an object from each archive:

```bash
xcrun nm -gU <static-root>/iosArm64/libpdfium.a \
  | grep FPDFAction_GetDest
xcrun nm -gU <static-root>/iosSimulatorArm64/libpdfium.a \
  | grep FPDFBitmap_CreateEx
```

## Updating PDFium

For the currently pinned version, the updater preserves the checked-in iOS
static archives:

```bash
./scripts/update-pdfium.sh chromium/7961
```

When changing to a new PDFium version, first build matching static iOS
archives, then pass their root directory to the updater:

```bash
PDFIUM_IOS_STATIC_ROOT=/absolute/path/to/static-root \
  ./scripts/update-pdfium.sh chromium/<build>
```

The updater intentionally fails when the requested PDFium version differs
from the current manifest and `PDFIUM_IOS_STATIC_ROOT` is missing. This
prevents new headers and non-iOS runtimes from being combined with an older
iOS static library.

The script:

1. Downloads the configured Android, JVM, and Wasm `.tgz` archives.
2. Calculates and records the SHA-256 digest of every downloaded archive.
3. Stages the supplied or preserved iOS static archives.
4. Extracts the Android and JVM runtime files.
5. Generates the browser module wrapper and adapter.
6. Calculates SHA-256 digests for the extracted JVM and Wasm runtime files.
7. Replaces the checked-in PDFium directories only after preparation
   succeeds.

The updater requires:

- `curl`;
- `tar`;
- `shasum`;
- standard POSIX shell tools.

Run the complete iOS build and update workflow on macOS with Xcode installed.

## Integrity and build checks

Run the layout and checksum verification after an update:

```bash
sh scripts/tests/update-pdfium-layout-test.sh
```

This verifies:

- JVM and browser manifests are identical;
- the browser module wrapper is present;
- the browser Wasm checksum matches the manifest;
- every extracted JVM runtime matches its recorded checksum;
- both iOS static archives exist;
- the cinterop definition embeds both the native bridge and PDFium archives
  with the required system linker options.

Compile the non-iOS targets:

```bash
./gradlew :pdf-core:compileAndroidMain
./gradlew :pdf-core:compileKotlinJvm
./gradlew :pdf-core:compileKotlinJs
./gradlew :pdf-core:compileKotlinWasmJs
```

On a compatible macOS host, link final frameworks for both Apple targets:

```bash
./gradlew \
  :shared:linkDebugFrameworkIosArm64 \
  :shared:linkDebugFrameworkIosSimulatorArm64
```

Inspect the final binaries to ensure no dynamic PDFium dependency remains:

```bash
otool -L shared/build/bin/iosArm64/debugFramework/Shared.framework/Shared
otool -L \
  shared/build/bin/iosSimulatorArm64/debugFramework/Shared.framework/Shared
```

Neither output may contain `libpdfium.dylib`.

Before publishing, publish to a local repository and link a separate consumer
project that declares only the Maven dependency. Building the library inside
this repository is not sufficient because local linker paths can hide missing
native implementation archives.

Do not update PDFium headers or non-iOS binaries without also supplying static
iOS archives built from the same revision.
