# Build and PDFium binaries

This document is intended for project maintainers. Library users do not need
to download or build PDFium manually.

## Binary provenance

All prebuilt PDFium binaries committed to this repository come from:

<https://github.com/bblanchon/pdfium-binaries>

The project downloads the archives from that repository's GitHub Releases:

```text
https://github.com/bblanchon/pdfium-binaries/releases/download/<version>/
```

The currently pinned release is:

```text
chromium/7961
```

The selected upstream flavor is `pdfium`, with PDF JavaScript and XFA
disabled. The pinned version, flavor, feature flags, archive checksums, and
runtime checksums are recorded in:

```text
pdf-core/pdfium/manifest.properties
```

The upstream `LICENSE`, `VERSION`, `args.gn`, and third-party license files are
kept under `pdf-core/pdfium`.

## Included targets

The updater downloads these upstream release classifiers:

| Target | Upstream classifier | Repository destination |
| --- | --- | --- |
| Android armeabi-v7a | `android-arm` | `pdf-core-android-native/src/main/jniLibs/armeabi-v7a/` |
| Android arm64-v8a | `android-arm64` | `pdf-core-android-native/src/main/jniLibs/arm64-v8a/` |
| Android x86 | `android-x86` | `pdf-core-android-native/src/main/jniLibs/x86/` |
| Android x86_64 | `android-x64` | `pdf-core-android-native/src/main/jniLibs/x86_64/` |
| iOS device arm64 | `ios-device-arm64` | `pdf-core/src/nativeInterop/cinterop/lib/iosArm64/` |
| iOS simulator arm64 | `ios-simulator-arm64` | `pdf-core/src/nativeInterop/cinterop/lib/iosSimulatorArm64/` |
| JVM macOS arm64 | `mac-arm64` | `pdf-core/src/jvmMain/resources/pdfium/darwin-aarch64/` |
| JVM macOS x64 | `mac-x64` | `pdf-core/src/jvmMain/resources/pdfium/darwin-x86-64/` |
| JVM Linux x64 | `linux-x64` | `pdf-core/src/jvmMain/resources/pdfium/linux-x86-64/` |
| JVM Windows x64 | `win-x64` | `pdf-core/src/jvmMain/resources/pdfium/win32-x86-64/` |
| Browser Wasm | `wasm` | `pdf-core/src/webMain/resources/pdfium/` |

Headers are extracted from the `android-arm64` archive into:

```text
pdf-core/src/nativeInterop/cinterop/include
```

## What is built in this repository

The PDFium libraries themselves are not compiled from Chromium source by this
project.

The project does compile its own thin native bridge:

- Android builds `pdf-core-android-native/src/main/cpp/pdfviewer_bridge.cpp`
  with CMake and links it to the prebuilt `libpdfium.so`.
- iOS builds `pdf-core-native/src/pdfviewer_core.cpp` and links it to the
  prebuilt `libpdfium.dylib`.

The browser adapter files in `scripts/pdfium-web` are maintained by this
project. The updater combines them with the upstream `pdfium.js` and
`pdfium.wasm`.

## Updating PDFium

Run the updater from the repository root:

```bash
./scripts/update-pdfium.sh chromium/<build>
```

For example, reinstalling the currently pinned version uses:

```bash
./scripts/update-pdfium.sh chromium/7961
```

The script:

1. Downloads every configured `.tgz` archive from
   `bblanchon/pdfium-binaries`.
2. Calculates and records the SHA-256 digest of every downloaded archive.
3. Extracts the Android, iOS, JVM, and Wasm runtime files into a temporary
   staging directory.
4. Rewrites the iOS dylib install name to `@rpath/libpdfium.dylib`.
5. Generates the browser module wrapper and adapter.
6. Calculates SHA-256 digests for the extracted JVM and Wasm runtime files.
7. Replaces the checked-in PDFium directories only after preparation
   succeeds.

The updater requires:

- `curl`
- `tar`
- `shasum`
- standard POSIX shell tools
- `install_name_tool` for preparing the iOS dylibs

Because `install_name_tool` is required, run the complete update workflow on
macOS.

## Integrity checks

Run the layout and checksum verification after an update:

```bash
./scripts/tests/update-pdfium-layout-test.sh
```

This verifies:

- JVM and browser manifests are identical;
- the browser module wrapper is present;
- the browser Wasm checksum matches the manifest;
- every extracted JVM runtime matches its recorded checksum.

The JVM runtime loader also checks the selected native library against the
recorded SHA-256 digest before loading it.

After the binary layout check, compile the affected targets:

```bash
./gradlew :pdf-core:compileAndroidMain
./gradlew :pdf-core:compileKotlinJvm
./gradlew :pdf-core:compileKotlinJs
./gradlew :pdf-core:compileKotlinWasmJs
```

On a compatible macOS host, also compile the iOS targets:

```bash
./gradlew :pdf-core:compileKotlinIosArm64
./gradlew :pdf-core:compileKotlinIosSimulatorArm64
```

Do not update a binary without updating its corresponding manifest digest and
upstream license files through `scripts/update-pdfium.sh`.
