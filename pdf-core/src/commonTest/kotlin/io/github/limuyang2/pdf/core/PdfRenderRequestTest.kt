package io.github.limuyang2.pdf.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PdfRenderRequestTest {
    @Test
    fun defaultsAreViewerSafe() {
        val request = PdfRenderRequest(PdfPixelSize(800, 600))

        assertEquals(PdfColor.White, request.backgroundColor)
        assertEquals(PdfRotation.Degrees0, request.rotation)
        assertTrue(request.renderAnnotations)
        assertFalse(request.grayscale)
        assertFalse(request.optimizeTextForLcd)
    }

    @Test
    fun colorChannelsUseArgbOrder() {
        val color = PdfColor(0x12345678u)

        assertEquals(0x12u.toUByte(), color.alpha)
        assertEquals(0x34u.toUByte(), color.red)
        assertEquals(0x56u.toUByte(), color.green)
        assertEquals(0x78u.toUByte(), color.blue)
    }

    @Test
    fun pixelFormatsDeclareTheirStorageSize() {
        assertEquals(4, PdfPixelFormat.Bgra8888.bytesPerPixel)
        assertEquals(4, PdfPixelFormat.Bgrx8888.bytesPerPixel)
        assertEquals(1, PdfPixelFormat.Gray8.bytesPerPixel)
    }
}
