@file:OptIn(ExperimentalWasmJsInterop::class)

package io.github.limuyang2.pdf.core.internal

import js.buffer.ArrayBuffer
import js.typedarrays.Uint8Array
import js.typedarrays.toByteArray
import js.typedarrays.toUint8Array
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise

private external interface WasmOpenedDocument : JsAny {
    val handle: Int
    val pageCount: Int
    val error: Int
}

private external interface WasmDocumentInformation : JsAny {
    val hasVersion: Boolean
    val version: Int
    val permissions: Double
    val securityRevision: Int
    val hasValidCrossReferenceTable: Boolean
}

private external interface WasmPageInformation : JsAny {
    val width: Double
    val height: Double
    val rotation: Int
    val hasBounds: Boolean
    val left: Double
    val bottom: Double
    val right: Double
    val top: Double
}

private external interface WasmPdfiumAdapter : JsAny {
    fun initialize(): Promise<JsAny?>

    fun destroy()

    fun open(
        bytes: Uint8Array<ArrayBuffer>,
        password: String?,
    ): WasmOpenedDocument

    fun close(handle: Int)

    fun documentInformation(handle: Int): WasmDocumentInformation

    fun metadata(
        handle: Int,
        tag: String,
    ): String?

    fun pageLabel(
        handle: Int,
        pageIndex: Int,
    ): String?

    fun pageInformation(
        handle: Int,
        pageIndex: Int,
    ): WasmPageInformation?

    fun render(
        handle: Int,
        pageIndex: Int,
        width: Int,
        height: Int,
        rotation: Int,
        backgroundArgb: Double,
        flags: Int,
    ): Uint8Array<ArrayBuffer>?

    fun extractText(
        handle: Int,
        pageIndex: Int,
        startCharacterIndex: Int,
        characterCount: Int,
    ): String?
}

internal actual val platformWebPdfiumInterop: WebPdfiumInterop =
    object : WebPdfiumInterop {
        override suspend fun initialize() {
            loadAdapterScript().await<JsAny?>()
            check(hasAdapter()) {
                "PDFium browser adapter did not register itself"
            }
            adapter().initialize().await<JsAny?>()
        }

        override fun destroy() {
            adapter().destroy()
        }

        override fun open(
            bytes: ByteArray,
            password: String?,
        ): WebOpenedDocument {
            val result = adapter().open(bytes.toUint8Array(), password)
            return WebOpenedDocument(
                handle = result.handle,
                pageCount = result.pageCount,
                errorCode = result.error,
            )
        }

        override fun close(handle: Int) {
            adapter().close(handle)
        }

        override fun documentInformation(handle: Int): WebDocumentInformation {
            val result = adapter().documentInformation(handle)
            return WebDocumentInformation(
                hasVersion = result.hasVersion,
                version = result.version,
                permissions = result.permissions.toLong().toUInt(),
                securityRevision = result.securityRevision,
                hasValidCrossReferenceTable =
                    result.hasValidCrossReferenceTable,
            )
        }

        override fun metadata(
            handle: Int,
            tag: String,
        ): String? = adapter().metadata(handle, tag)

        override fun pageLabel(
            handle: Int,
            pageIndex: Int,
        ): String? = adapter().pageLabel(handle, pageIndex)

        override fun pageInformation(
            handle: Int,
            pageIndex: Int,
        ): WebPageInformation? {
            val result =
                adapter().pageInformation(handle, pageIndex)
                    ?: return null
            return WebPageInformation(
                width = result.width,
                height = result.height,
                rotation = result.rotation,
                boundingBox =
                    if (result.hasBounds) {
                        WebPageBoundingBox(
                            left = result.left,
                            bottom = result.bottom,
                            right = result.right,
                            top = result.top,
                        )
                    } else {
                        null
                    },
            )
        }

        override fun render(
            handle: Int,
            pageIndex: Int,
            width: Int,
            height: Int,
            rotation: Int,
            backgroundArgb: UInt,
            flags: Int,
        ): ByteArray? =
            adapter()
                .render(
                    handle,
                    pageIndex,
                    width,
                    height,
                    rotation,
                    backgroundArgb.toDouble(),
                    flags,
                )?.toByteArray()

        override fun extractText(
            handle: Int,
            pageIndex: Int,
            startCharacterIndex: Int,
            characterCount: Int,
        ): String? =
            adapter().extractText(
                handle,
                pageIndex,
                startCharacterIndex,
                characterCount,
            )
    }

private fun adapter(): WasmPdfiumAdapter =
    js("globalThis.__pdfViewerPdfium")

private fun hasAdapter(): Boolean =
    js("typeof globalThis.__pdfViewerPdfium !== 'undefined'")

private fun loadAdapterScript(): Promise<JsAny?> =
    js(
        """{
            if (typeof globalThis.__pdfViewerPdfium !== 'undefined') {
                return Promise.resolve(null);
            }
            return new Promise((resolve, reject) => {
                const script = document.createElement('script');
                script.async = false;
                const base = globalThis.__pdfViewerPdfiumBaseUrl || 'pdfium/';
                script.src = new URL(base + 'pdfium-adapter.js', document.baseURI).href;
                script.onload = () => resolve(null);
                script.onerror = () => reject(
                    new Error('Unable to load PDFium browser adapter')
                );
                document.head.appendChild(script);
            });
        }""",
    )
