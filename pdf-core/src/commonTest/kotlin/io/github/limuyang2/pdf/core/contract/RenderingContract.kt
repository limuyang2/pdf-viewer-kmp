package io.github.limuyang2.pdf.core.contract

import io.github.limuyang2.pdf.core.PdfColor
import io.github.limuyang2.pdf.core.PdfPixelFormat
import io.github.limuyang2.pdf.core.PdfPixelSize
import io.github.limuyang2.pdf.core.PdfRect
import io.github.limuyang2.pdf.core.PdfRenderRequest
import io.github.limuyang2.pdf.core.PdfRotation
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal suspend fun PdfBackendContractScope.verifyRenderingContract() {
    val document = open(PdfContractFixture.RotatedPages)
    val page = document[0]
    val information = page.information()
    assertEquals(PdfRotation.Degrees90, information.rotation)
    assertEquals(612.0, information.size.width)
    assertEquals(792.0, information.size.height)

    val full =
        page.render(
            PdfRenderRequest(
                outputSize = PdfPixelSize(64, 96),
                backgroundColor = PdfColor.White,
            ),
        )
    assertEquals(64, full.width)
    assertEquals(96, full.height)
    assertEquals(64 * 4, full.stride)
    assertEquals(PdfPixelFormat.Bgra8888, full.format)
    assertEquals(full.stride * full.height, full.copyPixels().size)
    full.close()

    val region =
        page.render(
            PdfRenderRequest(
                outputSize = PdfPixelSize(32, 24),
                sourceRect = PdfRect(10.0, 20.0, 110.0, 140.0),
                backgroundColor = PdfColor.Transparent,
            ),
        )
    assertEquals(32, region.width)
    assertEquals(24, region.height)
    region.close()

    val withThumbnail = open(PdfContractFixture.ThumbnailPresent)
    assertNotNull(withThumbnail[0].thumbnail(PdfPixelSize(48, 48))).close()

    val withoutThumbnail = open(PdfContractFixture.ThumbnailAbsent)
    assertNull(withoutThumbnail[0].thumbnail(PdfPixelSize(48, 48)))
}
