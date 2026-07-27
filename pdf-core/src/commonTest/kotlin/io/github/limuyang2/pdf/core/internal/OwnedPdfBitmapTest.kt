package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfClosedException
import io.github.limuyang2.pdf.core.PdfPixelFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class OwnedPdfBitmapTest {
    @Test
    fun copiesPixelsWithoutExposingOwnedStorage() {
        val original = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val bitmap =
            OwnedPdfBitmap(
                width = 1,
                height = 2,
                stride = 4,
                pixels = original,
            )

        assertEquals(PdfPixelFormat.Bgra8888, bitmap.format)
        assertContentEquals(original, bitmap.copyPixels())

        val copy = bitmap.copyPixels()
        copy[0] = 99
        assertContentEquals(original, bitmap.copyPixels())

        val destination = ByteArray(11)
        bitmap.copyPixels(destination, destinationOffset = 3)
        assertContentEquals(
            byteArrayOf(0, 0, 0, 1, 2, 3, 4, 5, 6, 7, 8),
            destination,
        )
    }

    @Test
    fun validatesDestinationAndClosedState() {
        val bitmap =
            OwnedPdfBitmap(
                width = 1,
                height = 1,
                stride = 4,
                pixels = byteArrayOf(1, 2, 3, 4),
            )

        assertFailsWith<IllegalArgumentException> {
            bitmap.copyPixels(ByteArray(4), destinationOffset = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            bitmap.copyPixels(ByteArray(3))
        }

        bitmap.close()
        bitmap.close()
        assertTrue(bitmap.isClosed)
        assertFailsWith<PdfClosedException> { bitmap.copyPixels() }
    }
}
