const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "pdf-core-language-"));
const fixture = path.join(directory, "language.pdf");
try {
  for (const language of ["", "en-US", "he", "zh-Hant"]) {
    const stream = "BT /F1 18 Tf 20 250 Td (Language test) Tj ET";
    const objects = [
      `<< /Type /Catalog /Pages 2 0 R ${language ? `/Lang (${language})` : ""} >>`,
      "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
      "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 300] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
      "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
      `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`,
      "<< /Lang (wrong-info-language) >>",
    ];
    let pdf = "%PDF-1.7\n";
    const offsets = objects.map((body, index) => {
      const offset = pdf.length;
      pdf += `${index + 1} 0 obj\n${body}\nendobj\n`;
      return offset;
    });
    const xref = pdf.length;
    pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
    pdf += offsets.map(offset => `${String(offset).padStart(10, "0")} 00000 n \n`).join("");
    pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R /Info 6 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
    fs.writeFileSync(fixture, pdf);
    const result = spawnSync(path.resolve(process.argv[2]), [fixture, language], { stdio: "inherit" });
    assert.equal(result.status, 0, `Catalog language: ${language || "absent"}; ${result.error || ""}`);
  }
  console.log("Native catalog language checks passed.");
} finally {
  if (fs.existsSync(fixture)) fs.unlinkSync(fixture);
  fs.rmdirSync(directory);
}
