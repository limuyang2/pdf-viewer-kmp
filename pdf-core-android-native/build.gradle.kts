import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties

plugins {
    id("com.android.library")
    `maven-publish`
    signing
}

val libraryGroup = "io.github.limuyang2"
val libraryVersion = "0.1.0"

group = libraryGroup
version = libraryVersion

android {
    namespace = "io.github.limuyang2.pdf.core.bridge"
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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

var signingKeyId = ""
var signingPassword = ""
var secretKeyRingFile = ""
var ossrhUsername = ""
var ossrhPassword = ""


try {
    val localProperties: File = project.rootProject.file("local.properties")

    if (localProperties.exists()) {
        println("Found secret props file, loading props")
        val properties = Properties()

        InputStreamReader(FileInputStream(localProperties), Charsets.UTF_8).use { reader ->
            properties.load(reader)
        }
        signingKeyId = properties.getProperty("signing.keyId")
        signingPassword = properties.getProperty("signing.password")
        secretKeyRingFile = properties.getProperty("signing.secretKeyRingFile")
        ossrhUsername = properties.getProperty("ossrhUsername")
        ossrhPassword = properties.getProperty("ossrhPassword")

    } else {
        println("No props file, loading env vars")
    }
} catch (_: Exception) {
}


publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = libraryGroup
            artifactId = "pdf-core-android-native"
            version = libraryVersion

            pom {
                name.set("PDF Core Android Native")
                description.set(
                    "The Android PDFium native runtime used by PDF Core.",
                )
                url.set("https://github.com/limuyang2/pdf-viewer-kmp")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set(
                            "https://github.com/limuyang2/pdf-viewer-kmp/blob/main/LICENSE",
                        )
                        distribution.set(
                            "https://github.com/limuyang2/pdf-viewer-kmp/blob/main/LICENSE",
                        )
                    }
                }

                developers {
                    developer {
                        id.set("limuyang2")
                        name.set("limuyang")
                        email.set("limuyang2@hotmail.com")
                    }
                }

                scm {
                    url.set("https://github.com/limuyang2/pdf-viewer-kmp")
                    connection.set(
                        "scm:git@github.com:limuyang2/pdf-viewer-kmp.git",
                    )
                    developerConnection.set(
                        "scm:git@github.com:limuyang2/pdf-viewer-kmp.git",
                    )
                }
            }
        }
    }

    repositories {
        maven {
            name = "Maven"
            url =
                rootProject.layout.projectDirectory
                    .dir("RepoDir")
                    .asFile
                    .toURI()
        }
    }
}

afterEvaluate {
    publishing {
        publications.named<MavenPublication>("release") {
            from(components["release"])
        }
    }
}

gradle.taskGraph.whenReady {
    if (allTasks.any { it is Sign }) {

        allprojects {
            extra["signing.keyId"] = signingKeyId
            extra["signing.secretKeyRingFile"] = secretKeyRingFile
            extra["signing.password"] = signingPassword
        }
    }
}

signing {
    sign(publishing.publications)
}
