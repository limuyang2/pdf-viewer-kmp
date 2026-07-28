@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.limuyang2.pdf.core.internal

import io.github.limuyang2.pdf.core.PdfBitmap
import io.github.limuyang2.pdf.core.PdfBookmark
import io.github.limuyang2.pdf.core.PdfCapabilities
import io.github.limuyang2.pdf.core.PdfClosedException
import io.github.limuyang2.pdf.core.PdfDocumentInfo
import io.github.limuyang2.pdf.core.PdfInvalidFormatException
import io.github.limuyang2.pdf.core.PdfLink
import io.github.limuyang2.pdf.core.PdfLinkTarget
import io.github.limuyang2.pdf.core.PdfMetadata
import io.github.limuyang2.pdf.core.PdfNativeException
import io.github.limuyang2.pdf.core.PdfPageException
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPermissions
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfPoint
import io.github.limuyang2.pdf.core.PdfQuad
import io.github.limuyang2.pdf.core.PdfRect
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfRotation
import io.github.limuyang2.pdf.core.PdfSearchMatch
import io.github.limuyang2.pdf.core.PdfSearchOptions
import io.github.limuyang2.pdf.core.PdfSize
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfTextLayout
import io.github.limuyang2.pdf.core.PdfTextRange
import io.github.limuyang2.pdf.core.PdfUnsupportedFeatureException
import io.github.limuyang2.pdf.core.PdfVersion
import io.github.limuyang2.pdf.core.internal.nativecore.PDFV_ABI_VERSION
import io.github.limuyang2.pdf.core.internal.nativecore.PDFV_ERROR_BUFFER_TOO_SMALL
import io.github.limuyang2.pdf.core.internal.nativecore.PDFV_ERROR_CLOSED
import io.github.limuyang2.pdf.core.internal.nativecore.PDFV_ERROR_PAGE
import io.github.limuyang2.pdf.core.internal.nativecore.PDFV_OK
import io.github.limuyang2.pdf.core.internal.nativecore.PDFV_RENDER_ANNOTATIONS
import io.github.limuyang2.pdf.core.internal.nativecore.PDFV_RENDER_GRAYSCALE
import io.github.limuyang2.pdf.core.internal.nativecore.PDFV_RENDER_LCD_TEXT
import io.github.limuyang2.pdf.core.internal.nativecore.PDFV_SEARCH_CONSECUTIVE
import io.github.limuyang2.pdf.core.internal.nativecore.PDFV_SEARCH_MATCH_CASE
import io.github.limuyang2.pdf.core.internal.nativecore.PDFV_SEARCH_MATCH_WHOLE_WORD
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_close_document
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_destroy
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_destroy_search_result
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_document_info_t
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_document_t
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_extract_text_utf16
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_get_abi_version
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_get_document_info
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_get_metadata_utf16
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_get_page_info
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_get_page_label_utf16
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_get_page_links
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_get_search_match
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_get_search_rect
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_get_search_result_counts
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_initialize
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_link_t
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_open_memory
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_page_info_t
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_quad_t
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_rect_t
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_render_page
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_render_request_t
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_search_match_t
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_search_result_t
import io.github.limuyang2.pdf.core.internal.nativecore.pdfv_search_text_utf16
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.UShortVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value

internal object IosPdfiumBackend : PdfiumBackend {
    private val documents = mutableMapOf<Long, CPointer<pdfv_document_t>>()
    private var nextHandle = 1L

    override val capabilities: PdfCapabilities =
        PdfCapabilities(
            text = true,
            search = true,
            bookmarks = false,
            links = true,
            thumbnails = false,
            progressiveLoading = false,
            progressiveRendering = false,
            forms = false,
            editing = false,
            javascriptExecution = false,
            xfa = false,
        )

    override suspend fun initialize() {
        check(pdfv_get_abi_version() == PDFV_ABI_VERSION) {
            "The bundled PDF viewer native bridge has an incompatible ABI"
        }
        requireBridgeStatus(pdfv_initialize())
    }

    override fun destroy() {
        check(documents.isEmpty()) {
            "Cannot destroy PDFium while iOS documents are still open"
        }
        requireBridgeStatus(pdfv_destroy())
    }

    override fun open(
        source: PdfSource,
        password: String?,
    ): OpenedDocument {
        val bytes =
            when (source) {
                is PdfSource.Bytes -> source.data
                is PdfSource.RandomAccess ->
                    unsupported("random-access document sources on iOS")
            }
        if (bytes.isEmpty()) {
            throw PdfInvalidFormatException()
        }
        require(password?.contains('\u0000') != true) {
            "password must not contain a null character"
        }

        return memScoped {
            val document = alloc<CPointerVar<pdfv_document_t>>()
            val pageCount = alloc<IntVar>()
            val pdfiumError = alloc<UIntVar>()
            val status =
                bytes.usePinned { pinned ->
                    pdfv_open_memory(
                        data = pinned.addressOf(0).reinterpret<UByteVar>(),
                        size = bytes.size.toULong(),
                        password_utf8 = password,
                        document = document.ptr,
                        page_count = pageCount.ptr,
                        pdfium_error = pdfiumError.ptr,
                    )
                }
            if (status != PDFV_OK) {
                if (pdfiumError.value != 0u) {
                    throw pdfiumOpenFailure(
                        errorCode = pdfiumError.value.toULong(),
                        passwordWasSupplied = password != null,
                    )
                }
                requireBridgeStatus(status)
            }
            val opened =
                checkNotNull(document.value) {
                    "The shared PDFium bridge returned a null document"
                }
            check(nextHandle < Long.MAX_VALUE) {
                "The iOS PDF document handle space is exhausted"
            }
            val handle = nextHandle++
            documents[handle] = opened
            OpenedDocument(
                handle = NativeDocumentHandle(handle),
                pageCount = pageCount.value,
            )
        }
    }

    override fun close(document: NativeDocumentHandle) {
        val pointer =
            documents.remove(document.value)
                ?: throw PdfClosedException("PDF document")
        requireBridgeStatus(pdfv_close_document(pointer))
    }

    override fun documentInformation(
        document: NativeDocumentHandle,
    ): PdfDocumentInfo =
        memScoped {
            val info = alloc<pdfv_document_info_t>()
            requireBridgeStatus(
                pdfv_get_document_info(document.pointer(), info.ptr),
            )
            val encodedVersion = info.version
            PdfDocumentInfo(
                version =
                    if (info.has_version == 0) {
                        null
                    } else {
                        PdfVersion(
                            major = encodedVersion / 10,
                            minor = encodedVersion % 10,
                        )
                    },
                permissions = info.permissions.toPdfPermissions(),
                securityRevision = info.security_revision.takeIf { it >= 0 },
                hasValidCrossReferenceTable =
                    info.has_valid_cross_reference_table != 0,
                isLinearized =
                    when (info.is_linearized) {
                        0 -> false
                        1 -> true
                        else -> null
                    },
            )
        }

    override fun metadata(
        document: NativeDocumentHandle,
    ): PdfMetadata {
        fun value(tag: String): String? =
            readUtf16(emptyIsNull = true) { buffer, units, required ->
                pdfv_get_metadata_utf16(
                    document.pointer(),
                    tag,
                    buffer,
                    units,
                    required,
                )
            }
        return PdfMetadata(
            title = value("Title"),
            author = value("Author"),
            subject = value("Subject"),
            keywords = value("Keywords"),
            creator = value("Creator"),
            producer = value("Producer"),
            creationDate = value("CreationDate"),
            modificationDate = value("ModDate"),
        )
    }

    override fun bookmarks(
        document: NativeDocumentHandle,
    ): List<PdfBookmark> = unsupported("bookmarks on iOS")

    override fun pageLabel(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): String? =
        readUtf16(emptyIsNull = false) { buffer, units, required ->
            pdfv_get_page_label_utf16(
                document.pointer(),
                pageIndex,
                buffer,
                units,
                required,
            )
        }

    override fun pageInformation(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfPageInfo =
        memScoped {
            val info = alloc<pdfv_page_info_t>()
            requireBridgeStatus(
                pdfv_get_page_info(document.pointer(), pageIndex, info.ptr),
                pageIndex,
            )
            PdfPageInfo(
                size = PdfSize(info.width, info.height),
                rotation = pdfRotation(info.rotation),
                boundingBox =
                    if (info.has_bounding_box == 0) {
                        null
                    } else {
                        PdfRect(
                            left = info.bounding_box.left,
                            bottom = info.bounding_box.bottom,
                            right = info.bounding_box.right,
                            top = info.bounding_box.top,
                        )
                    },
            )
        }

    override fun render(
        document: NativeDocumentHandle,
        pageIndex: Int,
        request: PdfRenderRequest,
    ): PdfBitmap {
        if (request.sourceRect != null) {
            unsupported("cropped page rendering on iOS")
        }
        val width = request.outputSize.width
        val height = request.outputSize.height
        val strideLong = width.toLong() * 4L
        val byteCountLong = strideLong * height.toLong()
        require(strideLong <= Int.MAX_VALUE && byteCountLong <= Int.MAX_VALUE) {
            "render output is too large"
        }
        val pixels = ByteArray(byteCountLong.toInt())

        memScoped {
            val nativeRequest =
                alloc<pdfv_render_request_t> {
                    this.width = width
                    this.height = height
                    rotation = request.rotation.degrees / 90
                    background_argb = request.backgroundColor.argb
                    flags = 0u
                    if (request.renderAnnotations) {
                        flags = flags or PDFV_RENDER_ANNOTATIONS
                    }
                    if (request.grayscale) {
                        flags = flags or PDFV_RENDER_GRAYSCALE
                    }
                    if (request.optimizeTextForLcd) {
                        flags = flags or PDFV_RENDER_LCD_TEXT
                    }
                }
            val status =
                pixels.usePinned { pinned ->
                    pdfv_render_page(
                        document.pointer(),
                        pageIndex,
                        nativeRequest.ptr,
                        pinned.addressOf(0).reinterpret<UByteVar>(),
                        pixels.size.toULong(),
                    )
                }
            requireBridgeStatus(status, pageIndex)
        }
        return OwnedPdfBitmap(width, height, strideLong.toInt(), pixels)
    }

    override fun thumbnail(
        document: NativeDocumentHandle,
        pageIndex: Int,
        maximumSize: PdfPixelSize,
    ): PdfBitmap? = unsupported("embedded thumbnails on iOS")

    override fun extractText(
        document: NativeDocumentHandle,
        pageIndex: Int,
        range: PdfTextRange?,
    ): String =
        readUtf16(emptyIsNull = false) { buffer, units, required ->
            pdfv_extract_text_utf16(
                document.pointer(),
                pageIndex,
                range?.startCharacterIndex ?: 0,
                range?.characterCount ?: -1,
                buffer,
                units,
                required,
            )
        } ?: throw PdfPageException(pageIndex)

    override fun textLayout(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfTextLayout = unsupported("text layout on iOS")

    override fun search(
        document: NativeDocumentHandle,
        pageIndex: Int,
        query: String,
        options: PdfSearchOptions,
    ): List<PdfSearchMatch> =
        memScoped {
            val queryUtf16 =
                UShortArray(query.length + 1) { index ->
                    if (index < query.length) {
                        query[index].code.toUShort()
                    } else {
                        0u
                    }
                }
            var flags = 0u
            if (options.matchCase) flags = flags or PDFV_SEARCH_MATCH_CASE
            if (options.matchWholeWord) {
                flags = flags or PDFV_SEARCH_MATCH_WHOLE_WORD
            }
            if (options.consecutive) {
                flags = flags or PDFV_SEARCH_CONSECUTIVE
            }
            val resultReference =
                alloc<CPointerVar<pdfv_search_result_t>>()
            resultReference.value = null
            val status =
                queryUtf16.usePinned { pinned ->
                    pdfv_search_text_utf16(
                        document.pointer(),
                        pageIndex,
                        pinned.addressOf(0),
                        flags,
                        resultReference.ptr,
                    )
                }
            requireBridgeStatus(status, pageIndex)
            val result =
                resultReference.value
                    ?: throw PdfPageException(pageIndex)
            try {
                val matchCountReference = alloc<ULongVar>()
                val rectCountReference = alloc<ULongVar>()
                requireBridgeStatus(
                    pdfv_get_search_result_counts(
                        result,
                        matchCountReference.ptr,
                        rectCountReference.ptr,
                    ),
                    pageIndex,
                )
                val matchCount =
                    matchCountReference.value.checkedInt("search match")
                val rectCount =
                    rectCountReference.value.checkedInt("search rectangle")
                val nativeMatch = alloc<pdfv_search_match_t>()
                val nativeRect = alloc<pdfv_rect_t>()
                List(matchCount) { matchIndex ->
                    requireBridgeStatus(
                        pdfv_get_search_match(
                            result,
                            matchIndex.toULong(),
                            nativeMatch.ptr,
                        ),
                        pageIndex,
                    )
                    val firstRect =
                        nativeMatch.first_rect.checkedInt(
                            "search rectangle offset",
                        )
                    val matchRectCount =
                        nativeMatch.rect_count.checkedInt(
                            "search rectangle count",
                        )
                    require(
                        firstRect <= rectCount &&
                            matchRectCount <= rectCount - firstRect,
                    ) {
                        "The shared PDFium bridge returned invalid search bounds"
                    }
                    PdfSearchMatch(
                        range =
                            PdfTextRange(
                                startCharacterIndex =
                                    nativeMatch.start_character_index,
                                characterCount =
                                    nativeMatch.character_count,
                            ),
                        bounds =
                            List(matchRectCount) { rectIndex ->
                                requireBridgeStatus(
                                    pdfv_get_search_rect(
                                        result,
                                        (firstRect + rectIndex).toULong(),
                                        nativeRect.ptr,
                                    ),
                                    pageIndex,
                                )
                                PdfRect(
                                    left = nativeRect.left,
                                    bottom = nativeRect.bottom,
                                    right = nativeRect.right,
                                    top = nativeRect.top,
                                )
                            },
                    )
                }
            } finally {
                requireBridgeStatus(
                    pdfv_destroy_search_result(result),
                    pageIndex,
                )
            }
        }

    override fun links(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): List<PdfLink> =
        memScoped {
            val requiredLinks = alloc<ULongVar>()
            val requiredQuads = alloc<ULongVar>()
            val requiredStringBytes = alloc<ULongVar>()
            val sizeStatus =
                pdfv_get_page_links(
                    document.pointer(),
                    pageIndex,
                    null,
                    0u,
                    null,
                    0u,
                    null,
                    0u,
                    requiredLinks.ptr,
                    requiredQuads.ptr,
                    requiredStringBytes.ptr,
                )
            if (
                sizeStatus != PDFV_OK &&
                sizeStatus != PDFV_ERROR_BUFFER_TOO_SMALL
            ) {
                requireBridgeStatus(sizeStatus, pageIndex)
            }
            val linkCount = requiredLinks.value.checkedInt("link")
            val quadCount = requiredQuads.value.checkedInt("link quad")
            val stringByteCount =
                requiredStringBytes.value.checkedInt("link string")
            if (linkCount == 0) {
                return@memScoped emptyList()
            }

            val nativeLinks = allocArray<pdfv_link_t>(linkCount)
            val nativeQuads =
                if (quadCount > 0) {
                    allocArray<pdfv_quad_t>(quadCount)
                } else {
                    null
                }
            val stringBytes = ByteArray(stringByteCount)
            val status =
                if (stringBytes.isEmpty()) {
                    pdfv_get_page_links(
                        document.pointer(),
                        pageIndex,
                        nativeLinks,
                        linkCount.toULong(),
                        nativeQuads,
                        quadCount.toULong(),
                        null,
                        0u,
                        requiredLinks.ptr,
                        requiredQuads.ptr,
                        requiredStringBytes.ptr,
                    )
                } else {
                    stringBytes.usePinned { pinned ->
                        pdfv_get_page_links(
                            document.pointer(),
                            pageIndex,
                            nativeLinks,
                            linkCount.toULong(),
                            nativeQuads,
                            quadCount.toULong(),
                            pinned.addressOf(0).reinterpret<ByteVar>(),
                            stringBytes.size.toULong(),
                            requiredLinks.ptr,
                            requiredQuads.ptr,
                            requiredStringBytes.ptr,
                        )
                    }
                }
            requireBridgeStatus(status, pageIndex)

            List(linkCount) { index ->
                val link = nativeLinks[index]
                val firstQuad = link.first_quad.checkedInt("quad offset")
                val linkQuadCount = link.quad_count.checkedInt("quad count")
                require(
                    firstQuad <= quadCount &&
                        linkQuadCount <= quadCount - firstQuad,
                ) {
                    "The shared PDFium bridge returned invalid link bounds"
                }
                val bounds =
                    List(linkQuadCount) { quadIndex ->
                        val quad =
                            checkNotNull(nativeQuads)[firstQuad + quadIndex]
                        PdfQuad(
                            PdfPoint(quad.x1, quad.y1),
                            PdfPoint(quad.x2, quad.y2),
                            PdfPoint(quad.x3, quad.y3),
                            PdfPoint(quad.x4, quad.y4),
                        )
                    }
                val destination =
                    link.destination
                        .takeIf { it.page_index >= 0 }
                        ?.let {
                            pdfDestination(
                                pageIndex = it.page_index,
                                viewMode = it.view_mode.toInt(),
                                parameters =
                                    List(
                                        it.parameter_count
                                            .toInt()
                                            .coerceIn(0, 4),
                                    ) { parameterIndex ->
                                        it.parameters[parameterIndex]
                                    },
                                hasX = it.has_x != 0,
                                x = it.x,
                                hasY = it.has_y != 0,
                                y = it.y,
                                hasZoom = it.has_zoom != 0,
                                zoom = it.zoom,
                            )
                        }
                val stringOffset =
                    link.string_offset.checkedInt("link string offset")
                val stringLength =
                    link.string_length.checkedInt("link string length")
                require(
                    stringOffset <= stringBytes.size &&
                        stringLength <= stringBytes.size - stringOffset,
                ) {
                    "The shared PDFium bridge returned an invalid link string"
                }
                val value =
                    if (stringLength == 0) {
                        null
                    } else {
                        stringBytes
                            .copyOfRange(
                                stringOffset,
                                stringOffset + stringLength,
                            )
                            .decodeToString(throwOnInvalidSequence = false)
                    }
                PdfLink(
                    bounds = bounds,
                    target =
                        when (link.target_type.toInt()) {
                            PDF_LINK_TARGET_INTERNAL ->
                                PdfLinkTarget.Internal(
                                    checkNotNull(destination),
                                )
                            PDF_LINK_TARGET_URI ->
                                PdfLinkTarget.Uri(checkNotNull(value))
                            PDF_LINK_TARGET_REMOTE_DOCUMENT ->
                                PdfLinkTarget.RemoteDocument(
                                    value,
                                    destination,
                                )
                            else ->
                                PdfLinkTarget.Unsupported(
                                    link.native_action_type.toInt(),
                                )
                        },
                )
            }
        }

    private fun ULong.checkedInt(kind: String): Int {
        if (this > Int.MAX_VALUE.toULong()) {
            throw PdfNativeException(
                nativeErrorCode = 0,
                message = "The shared PDFium bridge returned oversized $kind data",
            )
        }
        return toInt()
    }

    private fun NativeDocumentHandle.pointer(): CPointer<pdfv_document_t> =
        documents[value]
            ?: throw PdfClosedException("PDF document")

    private fun readUtf16(
        emptyIsNull: Boolean,
        load: (
            buffer: CPointer<UShortVar>?,
            bufferUnits: ULong,
            requiredUnits: CPointer<ULongVar>,
        ) -> UInt,
    ): String? =
        memScoped {
            val required = alloc<ULongVar>()
            val sizeStatus = load(null, 0u, required.ptr)
            if (sizeStatus == PDFV_OK && required.value == 0uL) {
                return@memScoped null
            }
            if (sizeStatus != PDFV_ERROR_BUFFER_TOO_SMALL) {
                requireBridgeStatus(sizeStatus)
            }
            if (required.value > Int.MAX_VALUE.toULong()) {
                throw PdfNativeException(
                    nativeErrorCode = 0,
                    message = "The shared PDFium bridge returned oversized UTF-16 data",
                )
            }
            val utf16 = UShortArray(required.value.toInt())
            val status =
                utf16.usePinned { pinned ->
                    load(
                        pinned.addressOf(0),
                        utf16.size.toULong(),
                        required.ptr,
                    )
                }
            requireBridgeStatus(status)
            if (utf16.size <= 1 && emptyIsNull) {
                null
            } else {
                buildString(utf16.size - 1) {
                    repeat(utf16.size - 1) { index ->
                        append(utf16[index].toInt().toChar())
                    }
                }
            }
        }

    private fun requireBridgeStatus(
        status: UInt,
        pageIndex: Int? = null,
    ) {
        when (status) {
            PDFV_OK -> Unit
            PDFV_ERROR_CLOSED -> throw PdfClosedException("PDF document")
            PDFV_ERROR_PAGE ->
                throw PdfPageException(
                    pageIndex = pageIndex ?: 0,
                )
            else ->
                throw PdfNativeException(
                    nativeErrorCode = status.toInt(),
                    message = "The shared PDFium bridge failed with status $status",
                )
        }
    }

    private fun pdfRotation(value: Int): PdfRotation =
        when (value) {
            0 -> PdfRotation.Degrees0
            1 -> PdfRotation.Degrees90
            2 -> PdfRotation.Degrees180
            3 -> PdfRotation.Degrees270
            else -> throw PdfNativeException(
                nativeErrorCode = value,
                message = "PDFium returned an invalid page rotation: $value",
            )
        }

    private fun ULong.toPdfPermissions(): PdfPermissions {
        fun allows(pdfBitNumber: Int): Boolean =
            this and (1uL shl (pdfBitNumber - 1)) != 0uL
        return PdfPermissions(
            canPrint = allows(3),
            canModify = allows(4),
            canCopy = allows(5),
            canAnnotate = allows(6),
            canFillForms = allows(9),
            canExtractForAccessibility = allows(10),
            canAssemble = allows(11),
            canPrintHighQuality = allows(12),
        )
    }

    private fun unsupported(feature: String): Nothing =
        throw PdfUnsupportedFeatureException(feature)
}
