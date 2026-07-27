package io.github.limuyang2.pdf.core.internal

import com.sun.jna.Native
import java.io.InputStream
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.Properties

internal object JvmPdfiumLibraryLoader {
    private const val MANIFEST_RESOURCE = "/pdfium/manifest.properties"

    fun load(): JvmPdfiumLibrary {
        val library = extract()
        return Native.load(library.toAbsolutePath().toString(), JvmPdfiumLibrary::class.java)
    }

    internal fun extract(
        platform: JvmPdfiumPlatform = JvmPdfiumPlatform.current(),
        cacheRoot: Path =
            Path.of(
                System.getProperty("java.io.tmpdir"),
                "pdf-viewer-kmp",
                "pdfium",
            ),
    ): Path {
        val properties =
            resource(MANIFEST_RESOURCE).use { stream ->
                Properties().apply { load(stream) }
            }
        val version =
            properties.getProperty("version")
                ?: error("PDFium runtime manifest has no version")
        val expectedDigest =
            properties.getProperty("runtime.${platform.classifier}.sha256")
                ?: error(
                    "PDFium runtime manifest has no digest for " +
                        platform.classifier,
                )
        val versionDirectory = version.replace('/', '-')
        val destinationDirectory =
            cacheRoot
                .resolve(versionDirectory)
                .resolve(platform.classifier)
                .resolve(expectedDigest)
        val destination = destinationDirectory.resolve(platform.libraryName)
        val lockFile =
            destinationDirectory.parent.resolve("${platform.classifier}.lock")
        Files.createDirectories(lockFile.parent)

        FileChannel.open(
            lockFile,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        ).use { channel ->
            channel.lock().use {
                if (Files.isRegularFile(destination) &&
                    sha256(destination) == expectedDigest
                ) {
                    return destination
                }

                Files.createDirectories(destinationDirectory)
                val temporary =
                    Files.createTempFile(
                        destinationDirectory,
                        "${platform.libraryName}.",
                        ".part",
                    )
                try {
                    resource(platform.resourcePath).use { input ->
                        Files.copy(
                            input,
                            temporary,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                    val actualDigest = sha256(temporary)
                    check(actualDigest == expectedDigest) {
                        "Bundled PDFium digest mismatch for " +
                            "${platform.classifier}: expected $expectedDigest, " +
                            "got $actualDigest"
                    }
                    try {
                        Files.move(
                            temporary,
                            destination,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    } catch (_: AtomicMoveNotSupportedException) {
                        Files.move(
                            temporary,
                            destination,
                            StandardCopyOption.REPLACE_EXISTING,
                        )
                    }
                } finally {
                    Files.deleteIfExists(temporary)
                }
            }
        }
        return destination
    }

    private fun resource(path: String): InputStream =
        JvmPdfiumLibraryLoader::class.java.getResourceAsStream(path)
            ?: error("Bundled PDFium resource is missing: $path")

    internal fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }
}
