package io.github.limuyang2.pdf.core.internal

import com.sun.jna.IntegerType
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference

internal class NativeSize(
    value: Long = 0,
) : IntegerType(Native.SIZE_T_SIZE, value, true) {
    override fun toByte(): Byte = toLong().toByte()

    override fun toShort(): Short = toLong().toShort()
}

@Structure.FieldOrder("left", "top", "right", "bottom")
internal class FsRectF : Structure() {
    @JvmField var left: Float = 0f
    @JvmField var top: Float = 0f
    @JvmField var right: Float = 0f
    @JvmField var bottom: Float = 0f
}

@Suppress("FunctionName")
internal interface JvmPdfiumLibrary : Library {
    fun FPDF_InitLibrary()

    fun FPDF_DestroyLibrary()

    fun FPDF_LoadMemDocument64(
        data: Pointer,
        size: NativeSize,
        password: String?,
    ): Pointer?

    fun FPDF_GetLastError(): NativeLong

    fun FPDF_GetPageCount(document: Pointer): Int

    fun FPDF_CloseDocument(document: Pointer)

    fun FPDF_GetFileVersion(
        document: Pointer,
        version: IntByReference,
    ): Int

    fun FPDF_GetDocPermissions(document: Pointer): NativeLong

    fun FPDF_GetSecurityHandlerRevision(document: Pointer): Int

    fun FPDF_DocumentHasValidCrossReferenceTable(document: Pointer): Int

    fun FPDF_GetMetaText(
        document: Pointer,
        tag: String,
        buffer: Pointer?,
        bufferLength: NativeLong,
    ): NativeLong

    fun FPDF_GetPageLabel(
        document: Pointer,
        pageIndex: Int,
        buffer: Pointer?,
        bufferLength: NativeLong,
    ): NativeLong

    fun FPDF_LoadPage(
        document: Pointer,
        pageIndex: Int,
    ): Pointer?

    fun FPDF_ClosePage(page: Pointer)

    fun FPDF_GetPageWidthF(page: Pointer): Float

    fun FPDF_GetPageHeightF(page: Pointer): Float

    fun FPDFPage_GetRotation(page: Pointer): Int

    fun FPDF_GetPageBoundingBox(
        page: Pointer,
        rect: FsRectF,
    ): Int

    fun FPDFBitmap_CreateEx(
        width: Int,
        height: Int,
        format: Int,
        firstScan: Pointer,
        stride: Int,
    ): Pointer?

    fun FPDFBitmap_FillRect(
        bitmap: Pointer,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        color: Int,
    ): Int

    fun FPDFBitmap_Destroy(bitmap: Pointer)

    fun FPDF_RenderPageBitmap(
        bitmap: Pointer,
        page: Pointer,
        startX: Int,
        startY: Int,
        sizeX: Int,
        sizeY: Int,
        rotate: Int,
        flags: Int,
    )

    fun FPDFText_LoadPage(page: Pointer): Pointer?

    fun FPDFText_ClosePage(textPage: Pointer)

    fun FPDFText_CountChars(textPage: Pointer): Int

    fun FPDFText_GetText(
        textPage: Pointer,
        startIndex: Int,
        count: Int,
        result: Pointer,
    ): Int
}
