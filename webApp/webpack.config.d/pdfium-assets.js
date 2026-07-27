const fs = require("fs");
const path = require("path");

function findProjectRoot(start) {
  let current = start;
  while (current !== path.dirname(current)) {
    const resources = path.join(
      current,
      "pdf-core/src/webMain/resources/pdfium"
    );
    if (fs.existsSync(resources)) return current;
    current = path.dirname(current);
  }
  throw new Error("Unable to locate the bundled PDFium browser resources");
}

class PdfiumAssetsPlugin {
  apply(compiler) {
    compiler.hooks.thisCompilation.tap("PdfiumAssetsPlugin", compilation => {
      compilation.hooks.processAssets.tap(
        {
          name: "PdfiumAssetsPlugin",
          stage: compiler.webpack.Compilation.PROCESS_ASSETS_STAGE_ADDITIONAL
        },
        () => {
          const root = findProjectRoot(__dirname);
          const resources = path.join(
            root,
            "pdf-core/src/webMain/resources/pdfium"
          );
          for (const name of [
            "manifest.properties",
            "pdfium-adapter.js",
            "pdfium.js",
            "pdfium.wasm"
          ]) {
            compilation.emitAsset(
              `pdfium/${name}`,
              new compiler.webpack.sources.RawSource(
                fs.readFileSync(path.join(resources, name))
              )
            );
          }
        }
      );
    });
  }
}

config.plugins.push(new PdfiumAssetsPlugin());
