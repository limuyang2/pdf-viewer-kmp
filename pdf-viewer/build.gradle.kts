import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
    signing
}

kotlin {
    android {
        namespace = "io.github.limuyang2.pdf.viewer"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }

        androidResources {
            enable = true
        }
        withHostTestBuilder {
        }
    }

    iosArm64()
    iosSimulatorArm64()
    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":pdf-core"))
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            implementation(libs.kotlinx.coroutinesCore)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

val libraryGroup = "io.github.limuyang2"
val libraryVersion = "0.2.0"

extra["publicationGroup"] = libraryGroup
extra["publicationVersion"] = libraryVersion
extra["publicationArtifactId"] = "pdf-viewer"
extra["publicationName"] = "PDF Viewer"
extra["publicationDescription"] =
    "A Compose Multiplatform PDF viewer built on PDF Core."

apply(from = rootProject.file("gradle/publishing.gradle.kts"))
