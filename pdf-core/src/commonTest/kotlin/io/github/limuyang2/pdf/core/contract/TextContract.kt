package io.github.limuyang2.pdf.core.contract

import io.github.limuyang2.pdf.core.PdfSearchOptions
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal suspend fun PdfBackendContractScope.verifyTextContract() {
    val unicode = open(PdfContractFixture.UnicodeText)[0]
    assertEquals(PdfContractExpectations.UnicodeText, unicode.extractText())

    val generated = open(PdfContractFixture.GeneratedCharacters)[0]
    assertEquals(PdfContractExpectations.GeneratedText, generated.extractText())
    val generatedLayout = generated.textLayout()
    assertTrue(generatedLayout[13].isGenerated)
    assertTrue(generatedLayout[14].isGenerated)

    val hyphenated = open(PdfContractFixture.HyphenatedText)[0]
    assertEquals(
        PdfContractExpectations.HyphenatedText,
        hyphenated.extractText(),
    )
    assertTrue(hyphenated.textLayout()[6].isHyphen)

    val searchable = open(PdfContractFixture.SearchableText)[0]
    assertEquals(PdfContractExpectations.SearchableText, searchable.extractText())
    assertEquals(2, searchable.search("aaaa").size)
    assertEquals(
        7,
        searchable.search(
            query = "aaaa",
            options = PdfSearchOptions(consecutive = true),
        ).size,
    )

    assertEquals(2, generated.search("world").size)
    assertEquals(
        0,
        generated.search(
            query = "World",
            options = PdfSearchOptions(matchCase = true),
        ).size,
    )
    assertEquals(
        0,
        generated.search(
            query = "worl",
            options = PdfSearchOptions(matchWholeWord = true),
        ).size,
    )
}
