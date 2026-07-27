package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfUnsupportedFeatureException

internal data class JvmPdfiumPlatform(
    val classifier: String,
    val resourceDirectory: String,
    val libraryName: String,
) {
    val resourcePath: String
        get() = "/pdfium/$resourceDirectory/$libraryName"

    companion object {
        fun current(): JvmPdfiumPlatform =
            detect(
                osName = System.getProperty("os.name"),
                architecture = System.getProperty("os.arch"),
            )

        fun detect(
            osName: String,
            architecture: String,
        ): JvmPdfiumPlatform {
            val os = osName.lowercase()
            val arch = architecture.lowercase()
            val isArm64 = arch == "aarch64" || arch == "arm64"
            val isX64 = arch == "amd64" || arch == "x86_64" || arch == "x64"

            return when {
                (os.contains("mac") || os.contains("darwin")) && isArm64 ->
                    JvmPdfiumPlatform(
                        classifier = "mac-arm64",
                        resourceDirectory = "darwin-aarch64",
                        libraryName = "libpdfium.dylib",
                    )
                (os.contains("mac") || os.contains("darwin")) && isX64 ->
                    JvmPdfiumPlatform(
                        classifier = "mac-x64",
                        resourceDirectory = "darwin-x86-64",
                        libraryName = "libpdfium.dylib",
                    )
                os.contains("linux") && isX64 ->
                    JvmPdfiumPlatform(
                        classifier = "linux-x64",
                        resourceDirectory = "linux-x86-64",
                        libraryName = "libpdfium.so",
                    )
                os.contains("windows") && isX64 ->
                    JvmPdfiumPlatform(
                        classifier = "win-x64",
                        resourceDirectory = "win32-x86-64",
                        libraryName = "pdfium.dll",
                    )
                else ->
                    throw PdfUnsupportedFeatureException(
                        "JVM PDFium on os.name=$osName, os.arch=$architecture",
                    )
            }
        }
    }
}
