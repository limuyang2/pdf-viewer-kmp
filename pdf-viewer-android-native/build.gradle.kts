plugins {
    id("com.android.library")
}

android {
    namespace = "io.github.limuyang2.pdf.viewer.bridge"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")

        ndk {
            abiFilters +=
                setOf(
                    "armeabi-v7a",
                    "arm64-v8a",
                    "x86",
                    "x86_64",
                )
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}
