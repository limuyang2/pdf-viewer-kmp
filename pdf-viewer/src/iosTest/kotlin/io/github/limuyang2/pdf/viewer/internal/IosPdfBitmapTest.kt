package io.github.limuyang2.pdf.viewer.internal

import io.github.limuyang2.pdf.viewer.PdfClosedException
import io.github.limuyang2.pdf.viewer.PdfPixelFormat
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal class IosPdfBitmapTest {
    @Test
    fun copiesPixelsWithoutExposingOwnedStorage() {
        val bitmap =
            IosPdfBitmap(
                width = 1,
                height = 2,
                stride = 4,
                pixels = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            )
        assertEquals(PdfPixelFormat.Bgra8888, bitmap.format)

        val copy = bitmap.copyPixels()
        copy[0] = 99
        assertContentEquals(
            byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8),
            bitmap.copyPixels(),
        )

        val destination = ByteArray(10)
        bitmap.copyPixels(destination, destinationOffset = 2)
        assertContentEquals(
            byteArrayOf(0, 0, 1, 2, 3, 4, 5, 6, 7, 8),
            destination,
        )
    }

    @Test
    fun validatesDestinationAndClosedState() {
        val bitmap =
            IosPdfBitmap(
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
        assertFailsWith<PdfClosedException> {
            bitmap.copyPixels()
        }
    }
}
