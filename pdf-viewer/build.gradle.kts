import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.androidLint)
}

kotlin {
    val pdfiumIosDeploymentTarget = "26.0"

    android {
        namespace = "io.github.limuyang2.pdf.viewer"
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

    val pdfiumInteropDefinition = project.file("src/nativeInterop/cinterop/pdfium.def")
    val pdfiumHeaders = project.file("src/nativeInterop/cinterop/include")
    val pdfiumLibraries = project.file("src/nativeInterop/cinterop/lib")

    listOf(
        iosArm64() to "iosArm64",
        iosSimulatorArm64() to "iosSimulatorArm64",
    ).forEach { (target, libraryDirectory) ->
        val deploymentTargetOverride =
            "-Xoverride-konan-properties=" +
                "osVersionMin.ios_arm64=$pdfiumIosDeploymentTarget;" +
                "osVersionMin.ios_simulator_arm64=$pdfiumIosDeploymentTarget"

        target.compilations.getByName("main") {
            cinterops.create("pdfium") {
                definitionFile.set(pdfiumInteropDefinition)
                includeDirs(pdfiumHeaders)
            }
        }

        target.binaries.all {
            freeCompilerArgs += deploymentTargetOverride
            linkerOpts(
                "-L${pdfiumLibraries.resolve(libraryDirectory).absolutePath}",
                "-lpdfium",
            )
        }

        target.binaries.framework {
            baseName = "PdfViewerKit"
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(project(":pdf-viewer-android-native"))
        }

        commonMain.dependencies {
            implementation(libs.kotlin.stdlib)
            implementation(libs.kotlinx.coroutinesCore)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }

        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.core)
            implementation(libs.androidx.runner)
            implementation(libs.androidx.testExt.junit)
        }
    }
}
