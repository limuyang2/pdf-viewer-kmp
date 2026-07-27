import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.androidLint)
    `maven-publish`
    signing
}

kotlin {
    val pdfiumIosDeploymentTarget = "26.0"

    android {
        namespace = "io.github.limuyang2.pdf.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    val pdfiumInteropDefinition = project.file("src/nativeInterop/cinterop/pdfviewerCore.def")
    val pdfiumHeaders = project.file("src/nativeInterop/cinterop/include")
    val pdfiumLibraries = project.file("src/nativeInterop/cinterop/lib")
    val nativeCore = rootProject.file("pdf-core-native")
    val nativeCoreHeaders = nativeCore.resolve("include")
    val nativeCoreSource = nativeCore.resolve("src/pdfviewer_core.cpp")

    listOf(
        Triple(iosArm64(), "iosArm64", "iphoneos"),
        Triple(iosSimulatorArm64(), "iosSimulatorArm64", "iphonesimulator"),
    ).forEach { (target, libraryDirectory, sdk) ->
        val deploymentTargetOverride =
            "-Xoverride-konan-properties=" +
                "osVersionMin.ios_arm64=$pdfiumIosDeploymentTarget;" +
                "osVersionMin.ios_simulator_arm64=$pdfiumIosDeploymentTarget"
        val taskSuffix = libraryDirectory.replaceFirstChar(Char::uppercase)
        val nativeCoreOutput =
            layout.buildDirectory.dir("pdfviewerCore/$libraryDirectory")
        val nativeCoreObject =
            nativeCoreOutput.map { it.file("pdfviewer_core.o") }
        val nativeCoreArchive =
            nativeCoreOutput.map { it.file("libpdfviewer_core.a") }
        val compileNativeCore =
            tasks.register<Exec>("compilePdfviewerCore$taskSuffix") {
                inputs.file(nativeCoreSource)
                inputs.dir(nativeCoreHeaders)
                inputs.dir(pdfiumHeaders)
                outputs.file(nativeCoreObject)
                doFirst {
                    nativeCoreOutput.get().asFile.mkdirs()
                }
                commandLine(
                    "xcrun",
                    "--sdk",
                    sdk,
                    "clang++",
                    "-std=c++17",
                    "-arch",
                    "arm64",
                    if (sdk == "iphoneos") {
                        "-mios-version-min=$pdfiumIosDeploymentTarget"
                    } else {
                        "-mios-simulator-version-min=$pdfiumIosDeploymentTarget"
                    },
                    "-Wall",
                    "-Wextra",
                    "-Werror",
                    "-I${nativeCoreHeaders.absolutePath}",
                    "-I${pdfiumHeaders.absolutePath}",
                    "-c",
                    nativeCoreSource.absolutePath,
                    "-o",
                    nativeCoreObject.get().asFile.absolutePath,
                )
            }
        val archiveNativeCore =
            tasks.register<Exec>("archivePdfviewerCore$taskSuffix") {
                dependsOn(compileNativeCore)
                inputs.file(nativeCoreObject)
                outputs.file(nativeCoreArchive)
                commandLine(
                    "xcrun",
                    "--sdk",
                    sdk,
                    "ar",
                    "rcs",
                    nativeCoreArchive.get().asFile.absolutePath,
                    nativeCoreObject.get().asFile.absolutePath,
                )
            }

        target.compilations.getByName("main") {
            cinterops.create("pdfviewerCore") {
                definitionFile.set(pdfiumInteropDefinition)
                includeDirs(nativeCoreHeaders)
            }
        }

        target.binaries.all {
            freeCompilerArgs += deploymentTargetOverride
            linkerOpts(
                nativeCoreArchive.get().asFile.absolutePath,
                "-L${pdfiumLibraries.resolve(libraryDirectory).absolutePath}",
                "-lpdfium",
            )
            linkTaskProvider.configure {
                dependsOn(archiveNativeCore)
            }
        }

        target.binaries.framework {
            baseName = "PdfViewerKit"
        }
    }

    val syncPdfiumForIosSimulatorTests =
        tasks.register<Copy>("syncPdfiumForIosSimulatorTests") {
            dependsOn("linkDebugTestIosSimulatorArm64")
            from(
                pdfiumLibraries.resolve(
                    "iosSimulatorArm64/libpdfium.dylib",
                ),
            )
            into(
                layout.buildDirectory.dir(
                    "bin/iosSimulatorArm64/debugTest/Frameworks",
                ),
            )
        }
    tasks.named<KotlinNativeTest>("iosSimulatorArm64Test") {
        dependsOn(syncPdfiumForIosSimulatorTests)
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":pdf-core-android-native"))
        }

        commonMain.dependencies {
            api(libs.compose.runtimeAnnotation)
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.coroutinesCore)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }

        jvmMain.dependencies {
            implementation(libs.jna)
        }

        webMain.dependencies {
            implementation(libs.wrappers.browser)
        }

        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.core)
            implementation(libs.androidx.runner)
            implementation(libs.androidx.testExt.junit)
        }
    }
}

val libraryGroup = "io.github.limuyang2"
val libraryVersion = "0.1.1"

extra["publicationGroup"] = libraryGroup
extra["publicationVersion"] = libraryVersion
extra["publicationArtifactId"] = "pdf-core"
extra["publicationName"] = "PDF Core"
extra["publicationDescription"] =
    "A Kotlin Multiplatform PDF document and rendering library backed by PDFium."

apply(from = rootProject.file("gradle/publishing.gradle.kts"))
