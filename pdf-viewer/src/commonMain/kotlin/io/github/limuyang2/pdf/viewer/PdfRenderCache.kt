package io.github.limuyang2.pdf.viewer

internal class PdfRenderCache<T>(
    private val maximumByteCount: Long,
) {
    private val entries = LinkedHashMap<PdfRenderCacheKey, Entry<T>>()

    internal var byteCount: Long = 0
        private set

    init {
        require(maximumByteCount > 0) {
            "maximumByteCount must be positive"
        }
    }

    fun get(key: PdfRenderCacheKey): T? {
        val entry = entries.remove(key) ?: return null
        entries[key] = entry
        return entry.value
    }

    fun put(
        key: PdfRenderCacheKey,
        value: T,
        byteCount: Long,
    ) {
        require(byteCount > 0) { "byteCount must be positive" }

        entries.remove(key)?.let {
            this.byteCount -= it.byteCount
        }
        entries[key] = Entry(value, byteCount)
        this.byteCount += byteCount

        while (this.byteCount > maximumByteCount && entries.size > 1) {
            val oldestKey = entries.keys.first()
            val removed = entries.remove(oldestKey) ?: continue
            this.byteCount -= removed.byteCount
        }
    }

    fun clear() {
        entries.clear()
        byteCount = 0
    }

    private data class Entry<T>(
        val value: T,
        val byteCount: Long,
    )
}
