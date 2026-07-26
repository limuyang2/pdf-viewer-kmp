package io.github.limuyang2.pdf.viewer.contract

import io.github.limuyang2.pdf.viewer.PdfLinkTarget
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

internal suspend fun PdfBackendContractScope.verifyNavigationContract() {
    val internalLinks = open(PdfContractFixture.InternalLinks)[0].links()
    assertEquals(2, internalLinks.size)
    assertEquals(
        0,
        assertIs<PdfLinkTarget.Internal>(internalLinks[0].target)
            .destination.pageIndex,
    )

    val uriLinks = open(PdfContractFixture.UriLinks)[0].links()
    val uri = assertIs<PdfLinkTarget.Uri>(uriLinks.single().target)
    assertEquals("https://example.com/page.html", uri.uri)

    val bookmarks = open(PdfContractFixture.Bookmarks).bookmarks()
    assertEquals(2, bookmarks.size)
    assertEquals("A Good Beginning", bookmarks[0].title)
    assertEquals("Open Middle", bookmarks[1].title)
    val child = assertNotNull(bookmarks[1].children.singleOrNull())
    assertEquals("Open Middle Descendant", child.title)
    assertEquals(1, child.destination?.pageIndex)
}
