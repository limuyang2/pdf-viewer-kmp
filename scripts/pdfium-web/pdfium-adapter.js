(function (root) {
  "use strict";

  var adapterScript = document.currentScript;
  if (!adapterScript || !adapterScript.src) {
    throw new Error("Unable to determine the PDFium adapter script location");
  }

  var scriptDirectory = adapterScript.src.replace(/[?#].*$/, "");
  scriptDirectory = scriptDirectory.slice(0, scriptDirectory.lastIndexOf("/") + 1);
  var pdfiumModule;
  var pdfiumModulePromise;
  var pdfiumScriptPromise;
  var allocationCount = 0;
  var freeCount = 0;

  function loadPdfiumScript() {
    if (root.__pdfViewerCreatePdfiumModule) {
      return Promise.resolve();
    }

    if (!pdfiumScriptPromise) {
      pdfiumScriptPromise = new Promise(function (resolve, reject) {
        var script = document.createElement("script");
        script.async = false;
        script.src = scriptDirectory + "pdfium.js";
        script.onload = resolve;
        script.onerror = function () {
          pdfiumScriptPromise = undefined;
          reject(new Error("Unable to load PDFium module"));
        };
        document.head.appendChild(script);
      });
    }

    return pdfiumScriptPromise;
  }

  function trackAllocations(module) {
    var malloc = module._malloc;
    var free = module._free;

    if (typeof malloc === "function") {
      module._malloc = function (size) {
        allocationCount += 1;
        return malloc(size);
      };
    }
    if (typeof free === "function") {
      module._free = function (pointer) {
        freeCount += 1;
        return free(pointer);
      };
    }

    return module;
  }

  function initialize() {
    if (!pdfiumModulePromise) {
      pdfiumModulePromise = loadPdfiumScript()
        .then(function () {
          if (typeof root.__pdfViewerCreatePdfiumModule !== "function") {
            throw new Error("PDFium module factory was not loaded");
          }
          return root.__pdfViewerCreatePdfiumModule({
            locateFile: function (file) {
              return scriptDirectory + (file === "pdfium.wasm" ? "pdfium.wasm" : file);
            }
          });
        })
        .then(function (module) {
          pdfiumModule = trackAllocations(module);
          return pdfiumModule;
        }, function (error) {
          pdfiumModulePromise = undefined;
          throw error;
        });
    }

    return pdfiumModulePromise;
  }

  function destroy() {
    if (pdfiumModule && typeof pdfiumModule._FPDF_DestroyLibrary === "function") {
      pdfiumModule._FPDF_DestroyLibrary();
    }
    pdfiumModule = undefined;
    pdfiumModulePromise = undefined;
    allocationCount = 0;
    freeCount = 0;
  }

  function debugAllocationCounts() {
    return Object.freeze({
      allocations: allocationCount,
      frees: freeCount
    });
  }

  root.__pdfViewerPdfium = Object.freeze({
    initialize: initialize,
    destroy: destroy,
    debugAllocationCounts: debugAllocationCounts
  });
})(globalThis);
