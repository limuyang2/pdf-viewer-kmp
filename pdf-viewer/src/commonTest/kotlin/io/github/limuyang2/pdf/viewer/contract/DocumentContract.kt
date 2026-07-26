package io.github.limuyang2.pdf.viewer.contract

import io.github.limuyang2.pdf.viewer.PdfIncorrectPasswordException
import io.github.limuyang2.pdf.viewer.PdfInvalidFormatException
import io.github.limuyang2.pdf.viewer.PdfPasswordRequiredException
import io.github.limuyang2.pdf.viewer.PdfViewer
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal suspend fun PdfBackendContractScope.verifyDocumentContract() {
    val blank = open(PdfContractFixture.Blank)
    assertEquals(1, blank.pageCount)
    assertEquals(1, blank.information().version?.major)
    assertTrue(blank.information().hasValidCrossReferenceTable)

    val metadata = open(PdfContractFixture.Metadata).metadata()
    assertEquals("Microsoft Word", metadata.creator)
    assertEquals("D:20160411190039+00'00'", metadata.creationDate)
    assertEquals("D:20160411190039+00'00'", metadata.modificationDate)

    val labels = open(PdfContractFixture.PageLabels)
    assertEquals(7, labels.pageCount)
    assertEquals("i", labels.pageLabel(0))
    assertEquals("ii", labels.pageLabel(1))
    assertEquals("1", labels.pageLabel(2))
    assertEquals("2", labels.pageLabel(3))
    assertEquals("zzA", labels.pageLabel(4))
    assertEquals("zzB", labels.pageLabel(5))
    assertEquals("", labels.pageLabel(6))

    assertFailsWith<PdfPasswordRequiredException> {
        PdfViewer.open(source(PdfContractFixture.EncryptedUserPassword))
    }
    assertFailsWith<PdfIncorrectPasswordException> {
        PdfViewer.open(
            source(PdfContractFixture.EncryptedUserPassword),
            password = "wrong",
        )
    }
    open(
        PdfContractFixture.EncryptedUserPassword,
        PdfContractExpectations.UserPassword,
    )
    open(
        PdfContractFixture.EncryptedOwnerPassword,
        PdfContractExpectations.OwnerPassword,
    )

    assertFailsWith<PdfInvalidFormatException> {
        PdfViewer.open(source(PdfContractFixture.Corrupt))
    }
}
