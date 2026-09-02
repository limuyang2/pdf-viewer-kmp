package io.github.limuyang2.pdf.core

/**
 * A range using PDFium character indexes, not Kotlin string indexes.
 */
data class PdfTextRange(
    /** Index of the first covered character. */
    val startCharacterIndex: Int,
    /** Number of covered characters. */
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

    /** Index just past the last covered character. */
    val endCharacterIndexExclusive: Int
        get() = startCharacterIndex + characterCount
}

/**
 * Geometry and attributes of a single character within a [PdfTextLayout].
 */
data class PdfCharacter(
    /** PDFium character index within the page. */
    val characterIndex: Int,
    /** Unicode code point of the character. */
    val unicodeCodePoint: Int,
    /** Bounding box in PDF points, or `null` when unavailable. */
    val bounds: PdfRect?,
    /** Baseline origin in PDF points, or `null` when unavailable. */
    val origin: PdfPoint?,
    /** Font size in PDF points, or `null` when unavailable. */
    val fontSize: Double?,
    /** Baseline rotation in radians, or `null` when unavailable. */
    val angleRadians: Double?,
    /** Whether PDFium generated this character without a Unicode mapping. */
    val isGenerated: Boolean,
    /** Whether this character is an inserted soft hyphen. */
    val isHyphen: Boolean,
    /** Whether the character's Unicode mapping failed. */
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

/**
 * Full page text with per-character geometry.
 *
 * Not implemented by the current backends; [PdfPage.textLayout] throws
 * [PdfUnsupportedFeatureException].
 */
class PdfTextLayout internal constructor(
    /** The page text in reading order. */
    val text: String,
    characters: List<PdfCharacter>,
    private val rangeBounds: (PdfTextRange) -> List<PdfRect>,
) {
    private val characters: List<PdfCharacter> = characters.toList()

    /** Number of characters available through [get]. */
    val characterCount: Int
        get() = characters.size

    /** Returns the character at [characterIndex]. */
    operator fun get(characterIndex: Int): PdfCharacter =
        characters[characterIndex]

    /**
     * Returns rectangles covering [range]; a range spanning multiple lines
     * produces several rectangles.
     */
    fun bounds(range: PdfTextRange): List<PdfRect> {
        require(range.endCharacterIndexExclusive <= characterCount) {
            "range exceeds characterCount"
        }
        return rangeBounds(range).toList()
    }
}

/**
 * Options for [PdfPage.search].
 */
data class PdfSearchOptions(
    /** Matches upper- and lowercase letters exactly when `true`. */
    val matchCase: Boolean = false,
    /** Matches whole words only when `true`. */
    val matchWholeWord: Boolean = false,
    /**
     * Allows matches to overlap: the next match may start inside the
     * previous one instead of skipping past it.
     */
    val consecutive: Boolean = false,
)

/**
 * A single search hit on a page.
 */
data class PdfSearchMatch(
    /** Matched character range in PDFium character indexes. */
    val range: PdfTextRange,
    /** Rectangles covering the match in PDF page coordinates. */
    val bounds: List<PdfRect>,
)
