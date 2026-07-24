import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.androidLint)
}

kotlin {
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
        target.compilations.getByName("main") {
            cinterops.create("pdfium") {
                definitionFile.set(pdfiumInteropDefinition)
                includeDirs(pdfiumHeaders)
            }
        }

        target.binaries.framework {
            baseName = "PdfViewerKit"
            linkerOpts(
                "-L${pdfiumLibraries.resolve(libraryDirectory).absolutePath}",
                "-lpdfium",
            )
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlin.stdlib)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.core)
            implementation(libs.androidx.runner)
            implementation(libs.androidx.testExt.junit)
        }
    }
}
