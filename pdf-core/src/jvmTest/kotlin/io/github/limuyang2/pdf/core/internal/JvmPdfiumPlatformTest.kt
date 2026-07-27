package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfUnsupportedFeatureException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JvmPdfiumPlatformTest {
    @Test
    fun detectsSupportedPlatforms() {
        assertEquals(
            "mac-arm64",
            JvmPdfiumPlatform.detect("Mac OS X", "aarch64").classifier,
        )
        assertEquals(
            "mac-x64",
            JvmPdfiumPlatform.detect("Darwin", "x86_64").classifier,
        )
        assertEquals(
            "linux-x64",
            JvmPdfiumPlatform.detect("Linux", "amd64").classifier,
        )
        assertEquals(
            "win-x64",
            JvmPdfiumPlatform.detect("Windows 11", "amd64").classifier,
        )
    }

    @Test
    fun rejectsUnsupportedPlatforms() {
        assertFailsWith<PdfUnsupportedFeatureException> {
            JvmPdfiumPlatform.detect("Linux", "aarch64")
        }
    }
}
