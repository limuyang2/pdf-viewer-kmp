package io.github.limuyang2.pdf.viewer.contract

import io.github.limuyang2.pdf.viewer.PdfDocument
import io.github.limuyang2.pdf.viewer.PdfSource
import io.github.limuyang2.pdf.viewer.PdfViewer
import io.github.limuyang2.pdf.viewer.internal.PdfiumBackend
import io.github.limuyang2.pdf.viewer.internal.PdfiumBackendProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Behavior suite shared by every semantic PDFium backend.
 *
 * Platform adapters provide only a backend instance and fixture bytes.
 */
internal abstract class PdfBackendContract {
    protected abstract fun createBackend(): PdfiumBackend

    protected abstract suspend fun loadFixture(
        fixture: PdfContractFixture,
    ): ByteArray

    @Test
    fun documentContract() =
        runContract {
            verifyDocumentContract()
        }

    @Test
    fun renderingContract() =
        runContract {
            verifyRenderingContract()
        }

    @Test
    fun textContract() =
        runContract {
            verifyTextContract()
        }

    @Test
    fun navigationContract() =
        runContract {
            verifyNavigationContract()
        }

    private fun runContract(
        contract: suspend PdfBackendContractScope.() -> Unit,
    ) = runTest {
        val backend = createBackend()
        val restore = PdfiumBackendProvider.installForTesting(backend)
        val scope =
            PdfBackendContractScope(
                fixtureLoader = ::loadFixture,
            )
        try {
            scope.contract()
        } finally {
            scope.closeDocuments()
            restore()
        }
    }
}

internal class PdfBackendContractScope(
    private val fixtureLoader: suspend (PdfContractFixture) -> ByteArray,
) {
    private val documents = mutableListOf<PdfDocument>()

    suspend fun open(
        fixture: PdfContractFixture,
        password: String? = null,
    ): PdfDocument {
        val document =
            PdfViewer.open(
                source = PdfSource.Bytes(fixtureLoader(fixture)),
                password = password,
            )
        documents += document
        return document
    }

    suspend fun source(fixture: PdfContractFixture): PdfSource =
        PdfSource.Bytes(fixtureLoader(fixture))

    fun closeDocuments() {
        documents.asReversed().forEach(PdfDocument::close)
        documents.clear()
    }
}

internal enum class PdfContractFixture(
    val fileName: String,
) {
    Blank("about_blank.pdf"),
    RotatedPages("bug_1229106.pdf"),
    Metadata("bug_601362.pdf"),
    PageLabels("page_labels.pdf"),
    UnicodeText("unicode_text.pdf"),
    GeneratedCharacters("hello_world.pdf"),
    HyphenatedText("bug_781804.pdf"),
    SearchableText("find_text_consecutive.pdf"),
    InternalLinks("bug_821454.pdf"),
    UriLinks("uri_action.pdf"),
    Bookmarks("bookmarks.pdf"),
    EncryptedUserPassword("encrypted_hello_world_r2.pdf"),
    EncryptedOwnerPassword("encrypted_hello_world_r5.pdf"),
    Corrupt("corrupt.pdf"),
    ThumbnailPresent("simple_thumbnail.pdf"),
    ThumbnailAbsent("thumbnail_with_empty_stream.pdf"),
}

internal object PdfContractExpectations {
    const val UserPassword: String = "hôtel"
    const val OwnerPassword: String = "âge"
    const val UnicodeText: String = "Hello, 世界 🌍"
    const val GeneratedText: String = "Hello, world!\r\nGoodbye, world!"
    const val HyphenatedText: String = "Verita\u0002serum\r\nUser‐\r\ngenerated"
    const val SearchableText: String = "aaaaaaaaaa bbbbbbbbb"
}
