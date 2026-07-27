const path = require("path");

config.files.push({
  pattern: path.resolve(__dirname, "kotlin/pdfium/**/*"),
  included: false,
  served: true,
  watched: false
});
config.proxies["/pdfium/"] = "/base/kotlin/pdfium/";
