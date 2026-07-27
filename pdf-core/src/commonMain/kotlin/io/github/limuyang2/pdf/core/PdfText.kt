package io.github.limuyang2.pdf.core

/**
 * A range using PDFium character indexes, not Kotlin string indexes.
 */
data class PdfTextRange(
    val startCharacterIndex: Int,
    val characterCount: Int,
) {
    init {
        require(startCharacterIndex >= 0) {
            "startCharacterIndex must be non-negative"
        }
        require(characterCount >= 0) { "characterCount must be non-negative" }
        require(startCharacterIndex.toLong() + characterCount <= Int.MAX_VALUE) {
            "text range exceeds the supported character index range"
        }
    }

    val endCharacterIndexExclusive: Int
        get() = startCharacterIndex + characterCount
}

data class PdfCharacter(
    val characterIndex: Int,
    val unicodeCodePoint: Int,
    val bounds: PdfRect?,
    val origin: PdfPoint?,
    val fontSize: Double?,
    val angleRadians: Double?,
    val isGenerated: Boolean,
    val isHyphen: Boolean,
    val hasUnicodeMappingError: Boolean,
) {
    init {
        require(characterIndex >= 0) { "characterIndex must be non-negative" }
        require(unicodeCodePoint in 0..0x10FFFF) {
            "unicodeCodePoint must be a valid Unicode code point"
        }
        require(fontSize == null || fontSize.isFinite() && fontSize >= 0.0) {
            "fontSize must be null or finite and non-negative"
        }
        require(angleRadians == null || angleRadians.isFinite()) {
            "angleRadians must be null or finite"
        }
    }
}

class PdfTextLayout internal constructor(
    val text: String,
    characters: List<PdfCharacter>,
    private val rangeBounds: (PdfTextRange) -> List<PdfRect>,
) {
    private val characters: List<PdfCharacter> = characters.toList()

    val characterCount: Int
        get() = characters.size

    operator fun get(characterIndex: Int): PdfCharacter =
        characters[characterIndex]

    fun bounds(range: PdfTextRange): List<PdfRect> {
        require(range.endCharacterIndexExclusive <= characterCount) {
            "range exceeds characterCount"
        }
        return rangeBounds(range).toList()
    }
}

data class PdfSearchOptions(
    val matchCase: Boolean = false,
    val matchWholeWord: Boolean = false,
    val consecutive: Boolean = false,
)

data class PdfSearchMatch(
    val range: PdfTextRange,
    val bounds: List<PdfRect>,
)
