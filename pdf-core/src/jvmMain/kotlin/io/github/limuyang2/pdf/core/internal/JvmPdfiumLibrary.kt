package io.github.limuyang2.pdf.core.internal

import com.sun.jna.IntegerType
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.FloatByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference

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

@Structure.FieldOrder(
    "x1",
    "y1",
    "x2",
    "y2",
    "x3",
    "y3",
    "x4",
    "y4",
)
internal class FsQuadPointsF : Structure() {
    @JvmField var x1: Float = 0f
    @JvmField var y1: Float = 0f
    @JvmField var x2: Float = 0f
    @JvmField var y2: Float = 0f
    @JvmField var x3: Float = 0f
    @JvmField var y3: Float = 0f
    @JvmField var x4: Float = 0f
    @JvmField var y4: Float = 0f
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

    fun FPDFLink_Enumerate(
        page: Pointer,
        startPosition: IntByReference,
        link: PointerByReference,
    ): Int

    fun FPDFLink_GetAnnotRect(
        link: Pointer,
        rect: FsRectF,
    ): Int

    fun FPDFLink_CountQuadPoints(link: Pointer): Int

    fun FPDFLink_GetQuadPoints(
        link: Pointer,
        quadIndex: Int,
        quadPoints: FsQuadPointsF,
    ): Int

    fun FPDFLink_GetDest(
        document: Pointer,
        link: Pointer,
    ): Pointer?

    fun FPDFLink_GetAction(link: Pointer): Pointer?

    fun FPDFAction_GetType(action: Pointer): NativeLong

    fun FPDFAction_GetDest(
        document: Pointer,
        action: Pointer,
    ): Pointer?

    fun FPDFAction_GetFilePath(
        action: Pointer,
        buffer: Pointer?,
        bufferLength: NativeLong,
    ): NativeLong

    fun FPDFAction_GetURIPath(
        document: Pointer,
        action: Pointer,
        buffer: Pointer?,
        bufferLength: NativeLong,
    ): NativeLong

    fun FPDFDest_GetDestPageIndex(
        document: Pointer,
        destination: Pointer,
    ): Int

    fun FPDFDest_GetView(
        destination: Pointer,
        parameterCount: NativeLongByReference,
        parameters: Pointer,
    ): NativeLong

    fun FPDFDest_GetLocationInPage(
        destination: Pointer,
        hasX: IntByReference,
        hasY: IntByReference,
        hasZoom: IntByReference,
        x: FloatByReference,
        y: FloatByReference,
        zoom: FloatByReference,
    ): Int
}
