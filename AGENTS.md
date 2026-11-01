# Repository Guidelines

## Project Structure & Module Organization

This repository is organized into multiplatform libraries and sample demo apps:

- `pdf-core/`: Core PDFium wrapper without Compose UI dependencies; targets Android, iOS, JVM desktop, and Web (JS/Wasm).
- `pdf-viewer/`: Compose Multiplatform `PdfView` component providing zoom, panning, links, and search highlighting.
- `pdf-core-android-native/` & `pdf-core-native/`: C/C++ native bridge sources and static PDFium archives for Android JNI and iOS.
- `shared/`, `androidApp/`, `desktopApp/`, `webApp/`, `iosApp/`: Shared demo UI and platform-specific runner applications.

Source code resides in `<module>/src/<sourceSet>/kotlin`, native assets in `cpp/` or `resources/pdfium/`, and tests in `<sourceSet>Test/` (e.g., `commonTest`, `jvmTest`).

## Build, Test, and Development Commands

- `./gradlew build`: Compiles all modules and builds binaries.
- `./gradlew :pdf-core:jvmTest :pdf-viewer:jvmTest`: Runs JVM unit and contract tests quickly.
- `./gradlew allTests`: Executes test suites across supported platform targets.
- `./gradlew :desktopApp:run`: Launches the desktop sample app.
- `./gradlew :webApp:wasmJsBrowserDevelopmentRun`: Runs the Wasm browser demo locally.
- `./gradlew :dokkaGeneratePublicationHtml`: Generates aggregated Dokka API documentation.
- `sh scripts/tests/update-pdfium-layout-test.sh`: Verifies native PDFium binary integrity and runtime manifests.

## Coding Style & Naming Conventions

- **Kotlin**: 4 spaces, no tabs. Follow official Kotlin Coding Conventions.
- **Naming**: `PascalCase` for classes, interfaces, and `@Composable` functions; `camelCase` for functions, properties, and parameters.
- **KDoc**: All public API declarations in `pdf-core` and `pdf-viewer` must include clear KDoc documentation.
- **Native**: C++17 with `snake_case` naming for internal JNI/C bridge bindings.

## Testing Guidelines

- **Frameworks**: `kotlin.test`, `kotlinx.coroutines.test`, and AndroidX Test/JUnit.
- **Conventions**: Suffix test classes with `Test` (e.g., `PdfViewStateTest`, `JvmPdfiumBackendIntegrationTest`).
- Place platform-agnostic logic tests in `commonTest` and backend/platform bindings in `jvmTest`, `iosTest`, `webTest`, or `androidDeviceTest`.

## Commit & Pull Request Guidelines

- **Commit Format**: Follow Conventional Commits format (`type(scope): description`), e.g., `feat(core): add text search support` or `fix(ios): resolve static link errors`. Common types: `feat`, `fix`, `docs`, `chore`, `refactor`, `build`, `ci`.
- **Pull Requests**: Include a concise summary of changes, reference related issues, and attach screenshots or screen recordings for UI-related changes in `pdf-viewer` or demo apps. Verify `./gradlew check` passes before opening a PR.
