package io.github.limuyang2.pdf.core

import kotlin.jvm.JvmInline

/**
 * A non-premultiplied ARGB color encoded as `0xAARRGGBB`.
 */
@JvmInline
public value class PdfColor(
    public val argb: UInt,
) {
    public val alpha: UByte
        get() = (argb shr 24).toUByte()

    public val red: UByte
        get() = (argb shr 16).toUByte()

    public val green: UByte
        get() = (argb shr 8).toUByte()

    public val blue: UByte
        get() = argb.toUByte()

    public companion object {
        public val Transparent: PdfColor = PdfColor(0x00000000u)
        public val White: PdfColor = PdfColor(0xFFFFFFFFu)
        public val Black: PdfColor = PdfColor(0xFF000000u)
    }
}
