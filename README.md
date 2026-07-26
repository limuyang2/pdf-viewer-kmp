This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

### PDFium native dependencies

The `pdf-viewer` module vendors PDFium `chromium/7961` release binaries from
[`bblanchon/pdfium-binaries`](https://github.com/bblanchon/pdfium-binaries).
It uses the standard build without V8 or XFA.

The checked-in local dependencies cover:

- Android: `armeabi-v7a`, `arm64-v8a`, `x86`, and `x86_64`
- iOS: device arm64 and simulator arm64
- JVM: macOS arm64/x64, Windows x64, and Linux x64
- Web: the experimental Emscripten `pdfium.js` and `pdfium.wasm`

The upstream iOS binaries for this release require iOS 26.0. The iOS app and
the generated Kotlin frameworks use the same deployment target. PDFium is
linked through `@rpath/libpdfium.dylib`; the Xcode build embeds the matching
device or simulator binary in the app's `Frameworks` directory and signs it.

To replace them with another immutable release:

```bash
https_proxy=http://127.0.0.1:7890 \
http_proxy=http://127.0.0.1:7890 \
all_proxy=socks5://127.0.0.1:7891 \
./scripts/update-pdfium.sh chromium/<build>
```

The script downloads every platform package into a temporary directory,
records archive SHA-256 values in
`pdf-viewer/pdfium/manifest.properties`, then replaces the local platform
files. Review that manifest and run the platform builds before accepting an
upgrade.

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- Web tests:
  - Wasm target: `./gradlew :shared:wasmJsTest`
  - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com.cn/en-us/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform/#compose-multiplatform),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
