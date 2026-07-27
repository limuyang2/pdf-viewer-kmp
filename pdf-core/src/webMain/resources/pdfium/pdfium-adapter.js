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
  var initialized = false;
  var documentBuffers = new Map();

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
          pdfiumModule._FPDF_InitLibrary();
          initialized = true;
          return pdfiumModule;
        })
        .catch(function (error) {
          initialized = false;
          pdfiumModule = undefined;
          pdfiumModulePromise = undefined;
          throw error;
        });
    }

    return pdfiumModulePromise;
  }

  function destroy() {
    if (documentBuffers.size !== 0) {
      throw new Error("Cannot destroy PDFium while documents remain open");
    }
    if (initialized && pdfiumModule) {
      pdfiumModule._FPDF_DestroyLibrary();
    }
    initialized = false;
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

  function requireModule() {
    if (!initialized || !pdfiumModule) {
      throw new Error("PDFium is not initialized");
    }
    return pdfiumModule;
  }

  function allocateBytes(bytes) {
    var module = requireModule();
    var pointer = module._malloc(bytes.length);
    if (!pointer) {
      throw new Error("Could not allocate PDFium memory");
    }
    module.HEAPU8.set(bytes, pointer);
    return pointer;
  }

  function allocateUtf8(value) {
    if (value === null || value === undefined) {
      return 0;
    }
    var bytes = new TextEncoder().encode(value);
    var pointer = requireModule()._malloc(bytes.length + 1);
    if (!pointer) {
      throw new Error("Could not allocate PDFium string");
    }
    requireModule().HEAPU8.set(bytes, pointer);
    requireModule().HEAPU8[pointer + bytes.length] = 0;
    return pointer;
  }

  function open(bytes, password) {
    var module = requireModule();
    if (!(bytes instanceof Uint8Array) || bytes.length === 0) {
      return Object.freeze({ handle: 0, pageCount: 0, error: 3 });
    }
    var dataPointer = allocateBytes(bytes);
    var passwordPointer = allocateUtf8(password);
    try {
      var handle = module._FPDF_LoadMemDocument64(
        dataPointer,
        bytes.length,
        passwordPointer
      );
      if (!handle) {
        return Object.freeze({
          handle: 0,
          pageCount: 0,
          error: module._FPDF_GetLastError()
        });
      }
      var pageCount = module._FPDF_GetPageCount(handle);
      if (pageCount < 0) {
        module._FPDF_CloseDocument(handle);
        throw new Error("PDFium returned an invalid page count");
      }
      documentBuffers.set(handle, dataPointer);
      dataPointer = 0;
      return Object.freeze({
        handle: handle,
        pageCount: pageCount,
        error: 0
      });
    } finally {
      if (passwordPointer) module._free(passwordPointer);
      if (dataPointer) module._free(dataPointer);
    }
  }

  function close(handle) {
    var module = requireModule();
    var dataPointer = documentBuffers.get(handle);
    if (dataPointer === undefined) {
      throw new Error("Unknown PDFium document handle");
    }
    module._FPDF_CloseDocument(handle);
    module._free(dataPointer);
    documentBuffers.delete(handle);
  }

  function documentInformation(handle) {
    var module = requireModule();
    var versionPointer = module._malloc(4);
    try {
      var hasVersion = module._FPDF_GetFileVersion(handle, versionPointer);
      return Object.freeze({
        hasVersion: hasVersion !== 0,
        version: hasVersion ? module.HEAP32[versionPointer >> 2] : 0,
        permissions: module._FPDF_GetDocPermissions(handle) >>> 0,
        securityRevision: module._FPDF_GetSecurityHandlerRevision(handle),
        hasValidCrossReferenceTable:
          module._FPDF_DocumentHasValidCrossReferenceTable(handle) !== 0
      });
    } finally {
      module._free(versionPointer);
    }
  }

  function readUtf16(read, emptyAsNull) {
    var module = requireModule();
    var requiredBytes = read(0, 0) >>> 0;
    if (requiredBytes === 0) return null;
    if (requiredBytes < 2 || requiredBytes % 2 !== 0) {
      throw new Error("PDFium returned an invalid UTF-16 byte count");
    }
    var pointer = module._malloc(requiredBytes);
    try {
      var written = read(pointer, requiredBytes) >>> 0;
      if (written !== requiredBytes) {
        throw new Error("PDFium returned an invalid UTF-16 result length");
      }
      var bytes = module.HEAPU8.slice(pointer, pointer + requiredBytes - 2);
      var value = new TextDecoder("utf-16le").decode(bytes);
      return emptyAsNull && value.length === 0 ? null : value;
    } finally {
      module._free(pointer);
    }
  }

  function metadata(handle, tag) {
    return readUtf16(function (buffer, length) {
      var module = requireModule();
      var tagPointer = allocateUtf8(tag);
      try {
        return module._FPDF_GetMetaText(handle, tagPointer, buffer, length);
      } finally {
        module._free(tagPointer);
      }
    }, true);
  }

  function pageLabel(handle, pageIndex) {
    return readUtf16(function (buffer, length) {
      return requireModule()._FPDF_GetPageLabel(
        handle,
        pageIndex,
        buffer,
        length
      );
    }, false);
  }

  function withPage(handle, pageIndex, operation) {
    var module = requireModule();
    var page = module._FPDF_LoadPage(handle, pageIndex);
    if (!page) return null;
    try {
      return operation(module, page);
    } finally {
      module._FPDF_ClosePage(page);
    }
  }

  function pageInformation(handle, pageIndex) {
    return withPage(handle, pageIndex, function (module, page) {
      var boundsPointer = module._malloc(16);
      try {
        var rotation = module._FPDFPage_GetRotation(page);
        var nativeWidth = module._FPDF_GetPageWidthF(page);
        var nativeHeight = module._FPDF_GetPageHeightF(page);
        var swapsDimensions = rotation === 1 || rotation === 3;
        var hasBounds =
          module._FPDF_GetPageBoundingBox(page, boundsPointer) !== 0;
        var floatOffset = boundsPointer >> 2;
        return Object.freeze({
          width: swapsDimensions ? nativeHeight : nativeWidth,
          height: swapsDimensions ? nativeWidth : nativeHeight,
          rotation: rotation,
          hasBounds: hasBounds,
          left: hasBounds ? module.HEAPF32[floatOffset] : 0,
          top: hasBounds ? module.HEAPF32[floatOffset + 1] : 0,
          right: hasBounds ? module.HEAPF32[floatOffset + 2] : 0,
          bottom: hasBounds ? module.HEAPF32[floatOffset + 3] : 0
        });
      } finally {
        module._free(boundsPointer);
      }
    });
  }

  function render(
    handle,
    pageIndex,
    width,
    height,
    rotation,
    backgroundArgb,
    flags
  ) {
    return withPage(handle, pageIndex, function (module, page) {
      var stride = width * 4;
      var byteCount = stride * height;
      if (!Number.isSafeInteger(byteCount) || byteCount <= 0) {
        throw new Error("Invalid PDFium render output size");
      }
      var pixels = module._malloc(byteCount);
      if (!pixels) throw new Error("Could not allocate PDFium bitmap pixels");
      var bitmap = 0;
      try {
        bitmap = module._FPDFBitmap_CreateEx(width, height, 4, pixels, stride);
        if (!bitmap) throw new Error("PDFium could not create a bitmap");
        if (
          !module._FPDFBitmap_FillRect(
            bitmap,
            0,
            0,
            width,
            height,
            backgroundArgb
          )
        ) {
          throw new Error("PDFium could not initialize a bitmap");
        }
        module._FPDF_RenderPageBitmap(
          bitmap,
          page,
          0,
          0,
          width,
          height,
          rotation,
          flags
        );
        return module.HEAPU8.slice(pixels, pixels + byteCount);
      } finally {
        if (bitmap) module._FPDFBitmap_Destroy(bitmap);
        module._free(pixels);
      }
    });
  }

  function extractText(handle, pageIndex, startIndex, characterCount) {
    return withPage(handle, pageIndex, function (module, page) {
      var textPage = module._FPDFText_LoadPage(page);
      if (!textPage) return null;
      try {
        var totalCount = module._FPDFText_CountChars(textPage);
        var count =
          characterCount < 0 ? totalCount - startIndex : characterCount;
        if (
          totalCount < 0 ||
          startIndex < 0 ||
          startIndex > totalCount ||
          count < 0 ||
          count > totalCount - startIndex
        ) {
          throw new RangeError("Text range exceeds the page character count");
        }
        var byteCount = (count + 1) * 2;
        var pointer = module._malloc(byteCount);
        try {
          var written = module._FPDFText_GetText(
            textPage,
            startIndex,
            count,
            pointer
          );
          if (written !== count + 1) {
            throw new Error("PDFium returned an invalid text length");
          }
          return new TextDecoder("utf-16le").decode(
            module.HEAPU8.slice(pointer, pointer + count * 2)
          );
        } finally {
          module._free(pointer);
        }
      } finally {
        module._FPDFText_ClosePage(textPage);
      }
    });
  }

  root.__pdfViewerPdfium = Object.freeze({
    initialize: initialize,
    destroy: destroy,
    open: open,
    close: close,
    documentInformation: documentInformation,
    metadata: metadata,
    pageLabel: pageLabel,
    pageInformation: pageInformation,
    render: render,
    extractText: extractText,
    debugAllocationCounts: debugAllocationCounts
  });
})(globalThis);
