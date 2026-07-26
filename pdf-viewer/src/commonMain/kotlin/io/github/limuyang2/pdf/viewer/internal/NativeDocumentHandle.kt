package io.github.limuyang2.pdf.viewer.internal

import kotlin.jvm.JvmInline

/**
 * Opaque backend-owned document identity.
 *
 * The value has no public conversion and must never be interpreted by common
 * code.
 */
@JvmInline
internal value class NativeDocumentHandle(
    val value: Long,
)
