package io.github.limuyang2.pdf.core.internal

import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

internal actual val platformWebPdfiumInterop: WebPdfiumInterop =
    object : WebPdfiumInterop {
        override suspend fun initialize() {
            loadAdapter()
            adapter().initialize().unsafeCast<Promise<JsAny?>>().await()
        }

        override fun destroy() {
            adapter().destroy()
        }

        override fun open(
            bytes: ByteArray,
            password: String?,
        ): WebOpenedDocument {
            val input = Uint8Array(bytes.size)
            for (index in bytes.indices) {
                input.asDynamic()[index] = bytes[index].toInt() and 0xff
            }
            val result = adapter().open(input, password)
            return WebOpenedDocument(
                handle = result.handle as Int,
                pageCount = result.pageCount as Int,
                errorCode = result.error as Int,
            )
        }

        override fun close(handle: Int) {
            adapter().close(handle)
        }

        override fun documentInformation(handle: Int): WebDocumentInformation {
            val result = adapter().documentInformation(handle)
            return WebDocumentInformation(
                hasVersion = result.hasVersion as Boolean,
                version = result.version as Int,
                permissions = (result.permissions as Number).toLong().toUInt(),
                securityRevision = result.securityRevision as Int,
                hasValidCrossReferenceTable =
                    result.hasValidCrossReferenceTable as Boolean,
            )
        }

        override fun metadata(
            handle: Int,
            tag: String,
        ): String? = adapter().metadata(handle, tag) as String?

        override fun pageLabel(
            handle: Int,
            pageIndex: Int,
        ): String? = adapter().pageLabel(handle, pageIndex) as String?

        override fun pageInformation(
            handle: Int,
            pageIndex: Int,
        ): WebPageInformation? {
            val result =
                adapter().pageInformation(handle, pageIndex)
                    ?: return null
            return WebPageInformation(
                width = (result.width as Number).toDouble(),
                height = (result.height as Number).toDouble(),
                rotation = result.rotation as Int,
                boundingBox =
                    if (result.hasBounds as Boolean) {
                        WebPageBoundingBox(
                            left = (result.left as Number).toDouble(),
                            bottom = (result.bottom as Number).toDouble(),
                            right = (result.right as Number).toDouble(),
                            top = (result.top as Number).toDouble(),
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
        ): ByteArray? {
            val result =
                adapter().render(
                    handle,
                    pageIndex,
                    width,
                    height,
                    rotation,
                    backgroundArgb.toDouble(),
                    flags,
                ) ?: return null
            val length = result.length as Int
            return ByteArray(length) { index ->
                (result[index] as Int).toByte()
            }
        }

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
            ) as String?

        override fun links(
            handle: Int,
            pageIndex: Int,
        ): List<WebPdfLink>? {
            val result = adapter().links(handle, pageIndex) ?: return null
            return List(result.count as Int) { index ->
                val link = result.link(index)
                WebPdfLink(
                    bounds =
                        List(link.boundCount as Int) { boundIndex ->
                            val quad = link.bound(boundIndex)
                            WebPdfQuad(
                                x1 = (quad.x1 as Number).toDouble(),
                                y1 = (quad.y1 as Number).toDouble(),
                                x2 = (quad.x2 as Number).toDouble(),
                                y2 = (quad.y2 as Number).toDouble(),
                                x3 = (quad.x3 as Number).toDouble(),
                                y3 = (quad.y3 as Number).toDouble(),
                                x4 = (quad.x4 as Number).toDouble(),
                                y4 = (quad.y4 as Number).toDouble(),
                            )
                        },
                    targetType = link.targetType as Int,
                    actionType = link.actionType as Int,
                    destination =
                        link.destination?.let { destination ->
                            val parameters = destination.parameters
                            WebPdfDestination(
                                pageIndex = destination.pageIndex as Int,
                                viewMode = destination.viewMode as Int,
                                parameters =
                                    List(parameters.length as Int) {
                                        (parameters[it] as Number).toDouble()
                                    },
                                hasX = destination.hasX as Boolean,
                                x = (destination.x as Number).toDouble(),
                                hasY = destination.hasY as Boolean,
                                y = (destination.y as Number).toDouble(),
                                hasZoom = destination.hasZoom as Boolean,
                                zoom =
                                    (destination.zoom as Number).toDouble(),
                            )
                        },
                    value = link.value as String?,
                )
            }
        }
    }

private suspend fun loadAdapter() {
    if (hasAdapter()) return
    Promise<Unit> { resolve, reject ->
        val script = js("document.createElement('script')")
        script.async = false
        script.src =
            js(
                "new URL(" +
                    "(globalThis.__pdfViewerPdfiumBaseUrl || " +
                    "'pdfium/') + 'pdfium-adapter.js', " +
                    "document.baseURI).href",
            )
        script.onload = { resolve(Unit) }
        script.onerror = {
            reject(IllegalStateException("Unable to load PDFium browser adapter"))
        }
        js("document.head.appendChild(script)")
    }.await()
    check(hasAdapter()) { "PDFium browser adapter did not register itself" }
}

private fun hasAdapter(): Boolean =
    js("typeof globalThis.__pdfViewerPdfium !== 'undefined'") as Boolean

private fun adapter(): dynamic = js("globalThis.__pdfViewerPdfium")
