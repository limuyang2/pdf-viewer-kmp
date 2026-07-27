package io.github.limuyang2.pdf.core.contract

import io.github.limuyang2.pdf.core.PdfBookmark
import io.github.limuyang2.pdf.core.PdfCharacter
import io.github.limuyang2.pdf.core.PdfColor
import io.github.limuyang2.pdf.core.PdfDestination
import io.github.limuyang2.pdf.core.PdfDestinationView
import io.github.limuyang2.pdf.core.PdfDocumentInfo
import io.github.limuyang2.pdf.core.PdfIncorrectPasswordException
import io.github.limuyang2.pdf.core.PdfInvalidFormatException
import io.github.limuyang2.pdf.core.PdfLink
import io.github.limuyang2.pdf.core.PdfLinkTarget
import io.github.limuyang2.pdf.core.PdfMetadata
import io.github.limuyang2.pdf.core.PdfPageInfo
import io.github.limuyang2.pdf.core.PdfPasswordRequiredException
import io.github.limuyang2.pdf.core.PdfPermissions
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfPoint
import io.github.limuyang2.pdf.core.PdfRect
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfRotation
import io.github.limuyang2.pdf.core.PdfSearchMatch
import io.github.limuyang2.pdf.core.PdfSearchOptions
import io.github.limuyang2.pdf.core.PdfSize
import io.github.limuyang2.pdf.core.PdfSource
import io.github.limuyang2.pdf.core.PdfTextLayout
import io.github.limuyang2.pdf.core.PdfTextRange
import io.github.limuyang2.pdf.core.PdfVersion
import io.github.limuyang2.pdf.core.internal.FakePdfBitmap
import io.github.limuyang2.pdf.core.internal.FakePdfiumBackend
import io.github.limuyang2.pdf.core.internal.NativeDocumentHandle
import io.github.limuyang2.pdf.core.internal.OpenedDocument
import io.github.limuyang2.pdf.core.internal.PdfiumBackend

internal class FakePdfBackendContractTest : PdfBackendContract() {
    override fun createBackend(): PdfiumBackend = ContractFakePdfiumBackend()

    override suspend fun loadFixture(
        fixture: PdfContractFixture,
    ): ByteArray = "pdf-contract:${fixture.name}".encodeToByteArray()
}

private class ContractFakePdfiumBackend(
    private val delegate: FakePdfiumBackend = FakePdfiumBackend(),
) : PdfiumBackend by delegate {
    private val fixturesByHandle = mutableMapOf<Long, PdfContractFixture>()

    override fun open(
        source: PdfSource,
        password: String?,
    ): OpenedDocument {
        val fixture = source.fixture()
        when (fixture) {
            PdfContractFixture.Corrupt -> throw PdfInvalidFormatException()
            PdfContractFixture.EncryptedUserPassword ->
                validatePassword(password, PdfContractExpectations.UserPassword)
            PdfContractFixture.EncryptedOwnerPassword ->
                validatePassword(password, PdfContractExpectations.OwnerPassword)
            else -> Unit
        }

        val opened = delegate.open(source, password)
        fixturesByHandle[opened.handle.value] = fixture
        return opened.copy(pageCount = fixture.pageCount)
    }

    override fun close(document: NativeDocumentHandle) {
        fixturesByHandle.remove(document.value)
        delegate.close(document)
    }

    override fun documentInformation(
        document: NativeDocumentHandle,
    ): PdfDocumentInfo =
        PdfDocumentInfo(
            version = PdfVersion(1, 7),
            permissions = allPermissions,
            securityRevision =
                when (fixture(document)) {
                    PdfContractFixture.EncryptedUserPassword -> 2
                    PdfContractFixture.EncryptedOwnerPassword -> 5
                    else -> null
                },
            hasValidCrossReferenceTable = true,
            isLinearized = false,
        )

    override fun metadata(
        document: NativeDocumentHandle,
    ): PdfMetadata =
        PdfMetadata(
            title =
                null,
            author = null,
            subject = null,
            keywords = null,
            creator =
                if (fixture(document) == PdfContractFixture.Metadata) {
                    "Microsoft Word"
                } else {
                    null
                },
            producer = null,
            creationDate =
                if (fixture(document) == PdfContractFixture.Metadata) {
                    "D:20160411190039+00'00'"
                } else {
                    null
                },
            modificationDate =
                if (fixture(document) == PdfContractFixture.Metadata) {
                    "D:20160411190039+00'00'"
                } else {
                    null
                },
        )

    override fun pageLabel(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): String? =
        if (fixture(document) == PdfContractFixture.PageLabels) {
            listOf("i", "ii", "1", "2", "zzA", "zzB", "")[pageIndex]
        } else {
            null
        }

    override fun pageInformation(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfPageInfo =
        if (fixture(document) == PdfContractFixture.RotatedPages) {
            PdfPageInfo(
                size = PdfSize(612.0, 792.0),
                rotation = PdfRotation.Degrees90,
                boundingBox = PdfRect(0.0, 0.0, 612.0, 792.0),
            )
        } else {
            PdfPageInfo(
                size = PdfSize(612.0, 792.0),
                rotation = PdfRotation.Degrees0,
                boundingBox = PdfRect(0.0, 0.0, 612.0, 792.0),
            )
        }

    override fun render(
        document: NativeDocumentHandle,
        pageIndex: Int,
        request: PdfRenderRequest,
    ) = FakePdfBitmap(request.outputSize)

    override fun thumbnail(
        document: NativeDocumentHandle,
        pageIndex: Int,
        maximumSize: PdfPixelSize,
    ) = when (fixture(document)) {
        PdfContractFixture.ThumbnailPresent -> FakePdfBitmap(maximumSize)
        else -> null
    }

    override fun extractText(
        document: NativeDocumentHandle,
        pageIndex: Int,
        range: PdfTextRange?,
    ): String {
        val text = fixture(document).expectedText
        if (range == null) return text
        return text.substring(
            range.startCharacterIndex,
            range.endCharacterIndexExclusive,
        )
    }

    override fun textLayout(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): PdfTextLayout {
        val fixture = fixture(document)
        val text = fixture.expectedText
        val characters =
            text.mapIndexed { index, character ->
                PdfCharacter(
                    characterIndex = index,
                    unicodeCodePoint = character.code,
                    bounds = PdfRect(index.toDouble(), 0.0, index + 1.0, 1.0),
                    origin = PdfPoint(index.toDouble(), 0.0),
                    fontSize = 12.0,
                    angleRadians = 0.0,
                    isGenerated =
                        fixture == PdfContractFixture.GeneratedCharacters &&
                            index in 13..14,
                    isHyphen =
                        fixture == PdfContractFixture.HyphenatedText &&
                            index == 6,
                    hasUnicodeMappingError = false,
                )
            }
        return PdfTextLayout(
            text = text,
            characters = characters,
            rangeBounds = { range ->
                if (range.characterCount == 0) {
                    emptyList()
                } else {
                    listOf(
                        PdfRect(
                            range.startCharacterIndex.toDouble(),
                            0.0,
                            range.endCharacterIndexExclusive.toDouble(),
                            1.0,
                        ),
                    )
                }
            },
        )
    }

    override fun search(
        document: NativeDocumentHandle,
        pageIndex: Int,
        query: String,
        options: PdfSearchOptions,
    ): List<PdfSearchMatch> {
        val text = fixture(document).expectedText
        val source = if (options.matchCase) text else text.lowercase()
        val needle = if (options.matchCase) query else query.lowercase()
        val matches = mutableListOf<PdfSearchMatch>()
        var offset = 0
        while (offset <= source.length - needle.length) {
            val index = source.indexOf(needle, offset)
            if (index < 0) break
            val isWholeWord =
                (index == 0 || !source[index - 1].isLetterOrDigit()) &&
                    (index + needle.length == source.length ||
                        !source[index + needle.length].isLetterOrDigit())
            if (!options.matchWholeWord || isWholeWord) {
                matches +=
                    PdfSearchMatch(
                        range = PdfTextRange(index, needle.length),
                        bounds = emptyList(),
                    )
            }
            offset = index + if (options.consecutive) 1 else needle.length
        }
        return matches
    }

    override fun links(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): List<PdfLink> =
        when (fixture(document)) {
            PdfContractFixture.InternalLinks ->
                listOf(
                    PdfLink(
                        bounds = emptyList(),
                        target =
                            PdfLinkTarget.Internal(
                                PdfDestination(
                                    pageIndex = 0,
                                    view = PdfDestinationView.FitPage,
                                ),
                            ),
                    ),
                    PdfLink(
                        bounds = emptyList(),
                        target =
                            PdfLinkTarget.Internal(
                                PdfDestination(
                                    pageIndex = 0,
                                    view = PdfDestinationView.FitPage,
                                ),
                            ),
                    ),
                )
            PdfContractFixture.UriLinks ->
                listOf(
                    PdfLink(
                        bounds = emptyList(),
                        target =
                            PdfLinkTarget.Uri(
                                "https://example.com/page.html",
                            ),
                    ),
                )
            else -> emptyList()
        }

    override fun bookmarks(
        document: NativeDocumentHandle,
    ): List<PdfBookmark> =
        if (fixture(document) == PdfContractFixture.Bookmarks) {
            listOf(
                PdfBookmark(
                    title = "A Good Beginning",
                    destination = null,
                    target = null,
                    color = null,
                    children = emptyList(),
                ),
                PdfBookmark(
                    title = "Open Middle",
                    destination = null,
                    target = null,
                    color = PdfColor.Black,
                    children =
                        listOf(
                            PdfBookmark(
                                title = "Open Middle Descendant",
                                destination =
                                    PdfDestination(
                                        pageIndex = 1,
                                        view = PdfDestinationView.FitPage,
                                    ),
                                target = null,
                                color = null,
                                children = emptyList(),
                            ),
                        ),
                ),
            )
        } else {
            emptyList()
        }

    private fun fixture(document: NativeDocumentHandle): PdfContractFixture =
        checkNotNull(fixturesByHandle[document.value]) {
            "Unknown fake document handle ${document.value}"
        }

    private fun validatePassword(
        password: String?,
        expected: String,
    ) {
        if (password == null) throw PdfPasswordRequiredException()
        if (password != expected) throw PdfIncorrectPasswordException()
    }
}

private fun PdfSource.fixture(): PdfContractFixture {
    val marker = (this as PdfSource.Bytes).data.decodeToString()
    val fixtureName = marker.removePrefix("pdf-contract:")
    return PdfContractFixture.valueOf(fixtureName)
}

private val PdfContractFixture.pageCount: Int
    get() =
        when (this) {
            PdfContractFixture.RotatedPages -> 4
            PdfContractFixture.PageLabels -> 7
            PdfContractFixture.Bookmarks,
            PdfContractFixture.ThumbnailPresent,
            -> 2
            else -> 1
        }

private val PdfContractFixture.expectedText: String
    get() =
        when (this) {
            PdfContractFixture.UnicodeText -> PdfContractExpectations.UnicodeText
            PdfContractFixture.GeneratedCharacters -> PdfContractExpectations.GeneratedText
            PdfContractFixture.HyphenatedText -> PdfContractExpectations.HyphenatedText
            PdfContractFixture.SearchableText -> PdfContractExpectations.SearchableText
            else -> ""
        }

private val allPermissions =
    PdfPermissions(
        canPrint = true,
        canModify = true,
        canCopy = true,
        canAnnotate = true,
        canFillForms = true,
        canExtractForAccessibility = true,
        canAssemble = true,
        canPrintHighQuality = true,
    )
