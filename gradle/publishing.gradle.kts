import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.plugins.signing.SigningExtension
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.Properties

val publicationArtifactId = checkNotNull(project.extra["publicationArtifactId"] as? String) {
    "publicationArtifactId must be configured before applying publishing.gradle.kts"
}
val publicationName = checkNotNull(project.extra["publicationName"] as? String) {
    "publicationName must be configured before applying publishing.gradle.kts"
}
val publicationDescription = checkNotNull(project.extra["publicationDescription"] as? String) {
    "publicationDescription must be configured before applying publishing.gradle.kts"
}
val publicationGroup = checkNotNull(project.extra["publicationGroup"] as? String) {
    "publicationGroup must be configured before applying publishing.gradle.kts"
}
val publicationVersion = checkNotNull(project.extra["publicationVersion"] as? String) {
    "publicationVersion must be configured before applying publishing.gradle.kts"
}

val repositoryUrl = "https://github.com/limuyang2/pdf-viewer-kmp"

group = publicationGroup
version = publicationVersion

val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    val localProperties = Properties()
    InputStreamReader(
        FileInputStream(localPropertiesFile),
        Charsets.UTF_8,
    ).use(localProperties::load)

    val signingKeyId =
        localProperties.getProperty("signing.keyId", "")
    val signingPassword =
        localProperties.getProperty("signing.password", "")
    val signingSecretKeyRingFile =
        localProperties.getProperty("signing.secretKeyRingFile", "")

    if (
        signingKeyId.isNotBlank() &&
        signingPassword.isNotBlank() &&
        signingSecretKeyRingFile.isNotBlank()
    ) {
        extra["signing.keyId"] = signingKeyId
        extra["signing.password"] = signingPassword
        extra["signing.secretKeyRingFile"] = signingSecretKeyRingFile
    }
}

val emptyJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    description =
        "Assembles an empty Javadoc JAR required by Maven repository validation."
}

extensions.configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        groupId = publicationGroup
        version = publicationVersion

        artifactId =
            when (name) {
                "kotlinMultiplatform" -> publicationArtifactId
                "android" -> "$publicationArtifactId-android"
                "jvm" ->
                    "$publicationArtifactId-jvm".also {
                        artifact(emptyJavadocJar)
                    }
                "js" -> "$publicationArtifactId-js"
                "wasmJs" -> "$publicationArtifactId-wasm-js"
                "iosArm64" -> "$publicationArtifactId-ios-arm64"
                "iosSimulatorArm64" ->
                    "$publicationArtifactId-ios-simulator-arm64"
                else -> artifactId
            }

        pom {
            name.set(publicationName)
            description.set(publicationDescription)
            url.set(repositoryUrl)

            licenses {
                license {
                    name.set("MIT License")
                    url.set("$repositoryUrl/blob/main/LICENSE")
                    distribution.set("$repositoryUrl/blob/main/LICENSE")
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
                url.set(repositoryUrl)
                connection.set("scm:git@github.com:limuyang2/pdf-viewer-kmp.git")
                developerConnection.set("scm:git@github.com:limuyang2/pdf-viewer-kmp.git")
            }
        }
    }

    repositories {
        maven {
            name = "Maven"
            url = rootProject.layout.projectDirectory.dir("RepoDir").asFile.toURI()
        }
    }
}

extensions.configure<SigningExtension> {
    sign(extensions.getByType<PublishingExtension>().publications)
}
