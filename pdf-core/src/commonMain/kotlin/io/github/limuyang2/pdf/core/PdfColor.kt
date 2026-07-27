package io.github.limuyang2.pdf.core

import kotlin.jvm.JvmInline

/**
 * A non-premultiplied ARGB color encoded as `0xAARRGGBB`.
 */
@JvmInline
value class PdfColor(
    val argb: UInt,
) {
    val alpha: UByte
        get() = (argb shr 24).toUByte()

    val red: UByte
        get() = (argb shr 16).toUByte()

    val green: UByte
        get() = (argb shr 8).toUByte()

    val blue: UByte
        get() = argb.toUByte()

    companion object {
        val Transparent: PdfColor = PdfColor(0x00000000u)
        val White: PdfColor = PdfColor(0xFFFFFFFFu)
        val Black: PdfColor = PdfColor(0xFF000000u)
    }
}
