package io.github.limuyang2.pdf.viewer.contract

import io.github.limuyang2.pdf.viewer.PdfBookmark
import io.github.limuyang2.pdf.viewer.PdfCharacter
import io.github.limuyang2.pdf.viewer.PdfColor
import io.github.limuyang2.pdf.viewer.PdfDestination
import io.github.limuyang2.pdf.viewer.PdfDocumentInfo
import io.github.limuyang2.pdf.viewer.PdfIncorrectPasswordException
import io.github.limuyang2.pdf.viewer.PdfInvalidFormatException
import io.github.limuyang2.pdf.viewer.PdfLink
import io.github.limuyang2.pdf.viewer.PdfLinkTarget
import io.github.limuyang2.pdf.viewer.PdfMetadata
import io.github.limuyang2.pdf.viewer.PdfPageInfo
import io.github.limuyang2.pdf.viewer.PdfPasswordRequiredException
import io.github.limuyang2.pdf.viewer.PdfPermissions
import io.github.limuyang2.pdf.viewer.PdfPixelSize
import io.github.limuyang2.pdf.viewer.PdfPoint
import io.github.limuyang2.pdf.viewer.PdfRect
import io.github.limuyang2.pdf.viewer.PdfRenderRequest
import io.github.limuyang2.pdf.viewer.PdfRotation
import io.github.limuyang2.pdf.viewer.PdfSearchMatch
import io.github.limuyang2.pdf.viewer.PdfSearchOptions
import io.github.limuyang2.pdf.viewer.PdfSize
import io.github.limuyang2.pdf.viewer.PdfSource
import io.github.limuyang2.pdf.viewer.PdfTextLayout
import io.github.limuyang2.pdf.viewer.PdfTextRange
import io.github.limuyang2.pdf.viewer.PdfVersion
import io.github.limuyang2.pdf.viewer.internal.FakePdfBitmap
import io.github.limuyang2.pdf.viewer.internal.FakePdfiumBackend
import io.github.limuyang2.pdf.viewer.internal.NativeDocumentHandle
import io.github.limuyang2.pdf.viewer.internal.OpenedDocument
import io.github.limuyang2.pdf.viewer.internal.PdfiumBackend

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

    override suspend fun open(
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

    override suspend fun documentInformation(
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

    override suspend fun metadata(
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

    override suspend fun pageLabel(
        document: NativeDocumentHandle,
        pageIndex: Int,
    ): String? =
        if (fixture(document) == PdfContractFixture.PageLabels) {
            listOf("i", "ii", "1", "2", "zzA", "zzB", "")[pageIndex]
        } else {
            null
        }

    override suspend fun pageInformation(
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

    override suspend fun render(
        document: NativeDocumentHandle,
        pageIndex: Int,
        request: PdfRenderRequest,
    ) = FakePdfBitmap(request.outputSize)

    override suspend fun thumbnail(
        document: NativeDocumentHandle,
        pageIndex: Int,
        maximumSize: PdfPixelSize,
    ) = when (fixture(document)) {
        PdfContractFixture.ThumbnailPresent -> FakePdfBitmap(maximumSize)
        else -> null
    }

    override suspend fun extractText(
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

    override suspend fun textLayout(
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

    override suspend fun search(
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

    override suspend fun links(
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
                                    x = null,
                                    y = null,
                                    zoom = null,
                                ),
                            ),
                    ),
                    PdfLink(
                        bounds = emptyList(),
                        target =
                            PdfLinkTarget.Internal(
                                PdfDestination(
                                    pageIndex = 0,
                                    x = null,
                                    y = null,
                                    zoom = null,
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

    override suspend fun bookmarks(
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
                                        x = null,
                                        y = null,
                                        zoom = null,
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
