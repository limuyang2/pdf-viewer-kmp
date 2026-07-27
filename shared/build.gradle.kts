import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    val pdfiumIosDeploymentTarget = "26.0"
    val pdfiumLibraries =
        project(":pdf-core").projectDir.resolve("src/nativeInterop/cinterop/lib")

    listOf(
        iosArm64() to "iosArm64",
        iosSimulatorArm64() to "iosSimulatorArm64",
    ).forEach { (iosTarget, libraryDirectory) ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = false
            binaryOption("bundleId", "io.github.limuyang2.pdfdemo.shared")
            freeCompilerArgs +=
                "-Xoverride-konan-properties=" +
                    "osVersionMin.ios_arm64=$pdfiumIosDeploymentTarget;" +
                    "osVersionMin.ios_simulator_arm64=$pdfiumIosDeploymentTarget"
            linkerOpts(
                "-L${pdfiumLibraries.resolve(libraryDirectory).absolutePath}",
                "-lpdfium",
            )
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
    
    android {
       namespace = "io.github.limuyang2.pdfdemo.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(project(":pdf-viewer"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.navigation3.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
