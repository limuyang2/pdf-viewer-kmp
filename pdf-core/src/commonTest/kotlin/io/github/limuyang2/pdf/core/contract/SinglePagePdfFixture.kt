package io.github.limuyang2.pdf.core.contract

internal fun createSinglePageTestPdf(text: String): ByteArray {
    require(text.all { it.code in 0x20..0x7E && it != '(' && it != ')' })
    val stream = "BT /F1 18 Tf 20 250 Td ($text) Tj ET"
    val objects =
        listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] " +
                "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            "<< /Length ${stream.length} >>\nstream\n$stream\nendstream",
        )
    val output = StringBuilder("%PDF-1.7\n")
    val offsets = mutableListOf<Int>()
    objects.forEachIndexed { index, body ->
        offsets += output.length
        output.append("${index + 1} 0 obj\n$body\nendobj\n")
    }
    val xrefOffset = output.length
    output.append("xref\n0 ${objects.size + 1}\n")
    output.append("0000000000 65535 f \n")
    offsets.forEach { offset ->
        output.append(offset.toString().padStart(10, '0'))
        output.append(" 00000 n \n")
    }
    output.append(
        "trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n" +
            "startxref\n$xrefOffset\n%%EOF\n",
    )
    return output.toString().encodeToByteArray()
}
