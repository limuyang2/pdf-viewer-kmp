import java.io.FileInputStream
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.library")
    `maven-publish`
    signing
}

val libraryGroup = "io.github.limuyang2"
val libraryVersion = libs.versions.pdfAndroidNative.get()

group = libraryGroup
version = libraryVersion

android {
    namespace = "io.github.limuyang2.pdf.core.bridge"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt()) {
            minorApiLevel = libs.versions.android.compileSdkMinor.get().toInt()
        }
    }
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

// The native artifact is versioned on its own line, so publishing has to prove
// that the sources still match the content recorded for the current version.
val nativeReleaseInputs =
    listOf(
        layout.projectDirectory.dir("src/main").asFile,
        layout.projectDirectory.file("consumer-rules.pro").asFile,
    )
val nativeReleaseRecord =
    layout.projectDirectory.file("native-release.properties").asFile
val nativeReleaseRoot = layout.projectDirectory.asFile

// The roots are parameters so that task actions capture no script state.
val nativeReleaseFingerprint: (List<File>, File) -> String = { inputs, root ->
    val digest = MessageDigest.getInstance("SHA-256")
    inputs
        .flatMap { input ->
            if (input.isDirectory) {
                input.walkTopDown().filter { it.isFile }.toList()
            } else {
                listOf(input)
            }
        }.sortedBy { it.relativeTo(root).invariantSeparatorsPath }
        .forEach { file ->
            val path = file.relativeTo(root).invariantSeparatorsPath
            digest.update(path.toByteArray())
            digest.update(file.readBytes())
        }
    digest.digest().joinToString("") { "%02x".format(it) }
}

val checkNativeRelease =
    tasks.register("checkNativeRelease") {
        group = "verification"
        description =
            "Fails when the native sources no longer match the recorded pdf-core-android-native release."
        inputs.files(nativeReleaseInputs)
        inputs.property("version", libraryVersion)

        val record = nativeReleaseRecord
        val inputs = nativeReleaseInputs
        val root = nativeReleaseRoot
        val fingerprint = nativeReleaseFingerprint
        val version = libraryVersion

        doLast {
            val properties = Properties()
            if (record.isFile) record.inputStream().use { properties.load(it) }
            val recordedVersion = properties.getProperty("version")
            when {
                !record.isFile ->
                    throw GradleException(
                        "No pdf-core-android-native release record. " +
                            "Run :pdf-core-android-native:recordNativeRelease before publishing.",
                    )

                properties.getProperty("fingerprint") != fingerprint(inputs, root) ->
                    throw GradleException(
                        "pdf-core-android-native sources changed since $recordedVersion was recorded. " +
                            "Bump pdfAndroidNative in gradle/libs.versions.toml, " +
                            "then run :pdf-core-android-native:recordNativeRelease.",
                    )

                recordedVersion != version ->
                    throw GradleException(
                        "pdf-core-android-native is $version but the record describes $recordedVersion. " +
                            "Run :pdf-core-android-native:recordNativeRelease.",
                    )
            }
        }
    }

val recordNativeRelease =
    tasks.register("recordNativeRelease") {
        group = "publishing"
        description =
            "Records the current native sources as the content of this pdf-core-android-native version."
        val record = nativeReleaseRecord
        val inputs = nativeReleaseInputs
        val root = nativeReleaseRoot
        val fingerprint = nativeReleaseFingerprint
        val version = libraryVersion

        doLast {
            val connection =
                URI.create(
                    "https://repo1.maven.org/maven2/io/github/limuyang2/" +
                        "pdf-core-android-native/$version/pdf-core-android-native-$version.pom",
                ).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            val responseCode =
                try {
                    connection.responseCode
                } catch (exception: IOException) {
                    throw GradleException(
                        "Could not check whether pdf-core-android-native $version is on Maven Central.",
                        exception,
                    )
                }
            when (responseCode) {
                HttpURLConnection.HTTP_OK ->
                    throw GradleException(
                        "pdf-core-android-native $version is already published on Maven Central. " +
                            "Bump pdfAndroidNative in gradle/libs.versions.toml first.",
                    )

                HttpURLConnection.HTTP_NOT_FOUND -> Unit

                else ->
                    throw GradleException(
                        "Maven Central answered HTTP $responseCode for " +
                            "pdf-core-android-native $version.",
                    )
            }
            record.writeText(
                "version=$version\nfingerprint=${fingerprint(inputs, root)}\n",
            )
        }
    }

tasks.named("publishReleasePublicationToMavenRepository") {
    dependsOn(checkNativeRelease)
}
