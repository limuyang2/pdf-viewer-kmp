package io.github.limuyang2.pdf.core

import kotlin.jvm.JvmInline

/**
 * A non-premultiplied ARGB color encoded as `0xAARRGGBB`.
 */
@JvmInline
value class PdfColor(
    /** Packed `0xAARRGGBB` value. */
    val argb: UInt,
) {
    /** Alpha channel in `0..255`. */
    val alpha: UByte
        get() = (argb shr 24).toUByte()

    /** Red channel in `0..255`. */
    val red: UByte
        get() = (argb shr 16).toUByte()

    /** Green channel in `0..255`. */
    val green: UByte
        get() = (argb shr 8).toUByte()

    /** Blue channel in `0..255`. */
    val blue: UByte
        get() = argb.toUByte()

    companion object {
        /** Fully transparent black. */
        val Transparent: PdfColor = PdfColor(0x00000000u)

        /** Fully opaque white. */
        val White: PdfColor = PdfColor(0xFFFFFFFFu)

        /** Fully opaque black. */
        val Black: PdfColor = PdfColor(0xFF000000u)
    }
}
