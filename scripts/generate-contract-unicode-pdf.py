#!/usr/bin/env python3
"""Generate the deterministic Unicode text fixture used by backend contracts."""

from __future__ import annotations

import pathlib
import sys


def stream(data: bytes) -> bytes:
    return b"<< /Length %d >>\nstream\n" % len(data) + data + b"\nendstream"


def build_pdf() -> bytes:
    cmap = b"""\
/CIDInit /ProcSet findresource begin
12 dict begin
begincmap
/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
/CMapName /ContractUnicode def
/CMapType 2 def
1 begincodespacerange
<0000> <FFFF>
endcodespacerange
9 beginbfchar
<0001> <0048>
<0002> <0065>
<0003> <006C>
<0004> <006F>
<0005> <002C>
<0006> <0020>
<0007> <4E16>
<0008> <754C>
<0009> <D83CDF0D>
endbfchar
endcmap
CMapName currentdict /CMap defineresource pop
end
end
"""
    content = b"BT\n/F1 24 Tf\n20 40 Td\n<00010002000300030004000500060007000800060009> Tj\nET"
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Count 1 /Kids [3 0 R] >>",
        (
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 300 100] "
            b"/Resources << /Font << /F1 4 0 R >> >> /Contents 8 0 R >>"
        ),
        (
            b"<< /Type /Font /Subtype /Type0 /BaseFont /ContractUnicode "
            b"/Encoding /Identity-H /DescendantFonts [5 0 R] /ToUnicode 7 0 R >>"
        ),
        (
            b"<< /Type /Font /Subtype /CIDFontType2 /BaseFont /ContractUnicode "
            b"/CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) "
            b"/Supplement 0 >> /FontDescriptor 6 0 R /CIDToGIDMap /Identity "
            b"/DW 1000 >>"
        ),
        (
            b"<< /Type /FontDescriptor /FontName /ContractUnicode /Flags 4 "
            b"/FontBBox [0 -200 1000 900] /ItalicAngle 0 /Ascent 800 "
            b"/Descent -200 /CapHeight 700 /StemV 80 >>"
        ),
        stream(cmap),
        stream(content),
    ]

    output = bytearray(b"%PDF-1.7\n%\xA0\xF2\xA4\xF4\n")
    offsets = [0]
    for number, body in enumerate(objects, start=1):
        offsets.append(len(output))
        output.extend(f"{number} 0 obj\n".encode())
        output.extend(body)
        output.extend(b"\nendobj\n")

    xref_offset = len(output)
    output.extend(f"xref\n0 {len(objects) + 1}\n".encode())
    output.extend(b"0000000000 65535 f \n")
    for offset in offsets[1:]:
        output.extend(f"{offset:010d} 00000 n \n".encode())
    output.extend(
        (
            f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\n"
            f"startxref\n{xref_offset}\n%%EOF\n"
        ).encode()
    )
    return bytes(output)


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("usage: generate-contract-unicode-pdf.py OUTPUT.pdf")
    pathlib.Path(sys.argv[1]).write_bytes(build_pdf())


if __name__ == "__main__":
    main()
