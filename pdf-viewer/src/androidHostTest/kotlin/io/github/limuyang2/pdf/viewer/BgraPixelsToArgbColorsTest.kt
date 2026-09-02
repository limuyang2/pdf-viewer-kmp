package io.github.limuyang2.pdf.viewer

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BgraPixelsToArgbColorsTest {
    @Test
    fun convertsKnownBgraChannelsToArgbIntegers() {
        assertContentEquals(
            intArrayOf(0x78563412),
            bgraPixelsToArgbColors(
                source = byteArrayOf(0x12, 0x34, 0x56, 0x78),
                width = 1,
                height = 1,
                stride = 4,
            ),
        )
    }

    @Test
    fun bulkConversionMatchesChannelByChannelConversion() {
        verifyAgainstReference(width = 13, height = 7, paddingBytes = 0)
        verifyAgainstReference(width = 13, height = 7, paddingBytes = 8)
    }

    @Test
    fun rejectsInvalidBuffersAndStrides() {
        assertFailsWith<IllegalArgumentException> {
            bgraPixelsToArgbColors(ByteArray(12), width = 4, height = 1, stride = 12)
        }
        assertFailsWith<IllegalArgumentException> {
            bgraPixelsToArgbColors(ByteArray(18), width = 4, height = 1, stride = 18)
        }
        assertFailsWith<IllegalArgumentException> {
            bgraPixelsToArgbColors(ByteArray(20), width = 4, height = 2, stride = 20)
        }
    }

    private fun verifyAgainstReference(
        width: Int,
        height: Int,
        paddingBytes: Int,
    ) {
        val stride = width * 4 + paddingBytes
        val source = Random(42).nextBytes(stride * height)
        assertContentEquals(
            referenceConversion(source, width, height, stride),
            bgraPixelsToArgbColors(source, width, height, stride),
        )
    }

    private fun referenceConversion(
        source: ByteArray,
        width: Int,
        height: Int,
        stride: Int,
    ): IntArray =
        IntArray(width * height) { index ->
            val row = index / width
            val column = index % width
            val offset = row * stride + column * 4
            val blue = source[offset].toInt() and 0xff
            val green = source[offset + 1].toInt() and 0xff
            val red = source[offset + 2].toInt() and 0xff
            val alpha = source[offset + 3].toInt() and 0xff
            (alpha shl 24) or (red shl 16) or (green shl 8) or blue
        }
}
