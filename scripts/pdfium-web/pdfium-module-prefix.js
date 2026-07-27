(function (root) {
  "use strict";
  root.__pdfViewerCreatePdfiumModule = function (configuration) {
    return new Promise(function (resolve, reject) {
      var Module = Object.assign({}, configuration, {
        onAbort: reject,
        onRuntimeInitialized: function () { resolve(Module); }
      });
