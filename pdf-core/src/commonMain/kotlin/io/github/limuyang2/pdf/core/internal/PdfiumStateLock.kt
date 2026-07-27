package io.github.limuyang2.pdf.core.internal

/**
 * Short, non-suspending critical section for document lifecycle flags.
 */
internal expect fun <T> withPdfiumStateLock(operation: () -> T): T
