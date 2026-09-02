# 更新日志

[English](CHANGELOG.md)

## 0.3.0 - 2026-09-02

### PDF Core

- JVM 打开文档失败时始终释放 JNA 文档内存，同时避免重复句柄检查破坏句柄表。
- 在共享契约及各平台集成测试中验证非对称背景色的 BGRA 渲染结果。
- 补充同步和挂起关闭的行为说明，包括重复抛出清理异常及 UI 线程阻塞风险。

### PDF Viewer

- Android 使用小端缓冲区批量读取完成 BGRA 到 ARGB 的转换，同时支持带行填充的
  stride。
- 通过 Compose side effect 发布文档绑定和布局信息，避免在组合期间修改 Viewer
  状态。

## 0.2.2 - 2026-07-29

本版本使发布的 iOS 产物可以被外部工程直接引用。

### PDF Core

- 将匹配架构的静态 `libpdfium.a` 和 `libpdfviewer_core.a` 一并嵌入各
  cinterop KLIB 发布物。
- 修复外部 Kotlin Multiplatform 工程链接最终 iOS framework 时缺少
  `FPDF*` 符号的问题，包括 `FPDFAction_GetDest`、
  `FPDFBitmap_CreateEx` 等符号。
- 将所需的 libc++、CoreFoundation 和 CoreGraphics 链接参数传递给下游
  使用方。
- 移除对 `@rpath/libpdfium.dylib` 的运行时依赖，接入方无需再复制或签名
  PDFium 动态库。
- 支持真机 arm64 和模拟器 arm64，iOS 最低部署版本调整为 14.0。
- 更新 PDFium 维护脚本，可保留当前静态 iOS 归档，或通过
  `PDFIUM_IOS_STATIC_ROOT` 提供匹配版本的归档。

### iOS Demo

- 移除不再需要的 PDFium 动态库嵌入与签名逻辑。

## 0.2.1 - 2026-07-29

本版本修复 iOS 构建和运行时集成问题。

### PDF Core

- 将 `libpdfviewer_core.a` 嵌入 cinterop KLIB，修复最终 Kotlin/Native
  framework 链接时缺少 `pdfv_*` 符号的问题。
- 确保真机 arm64 和模拟器 arm64 构建都会先生成并包含对应架构的原生
  PDF bridge。

### iOS Demo

- 根据真机或模拟器平台，将对应的 `libpdfium.dylib` 自动复制到 App 的
  `Frameworks` 目录并完成代码签名。
- 修复启用 Xcode Kotlin 构建支持时跳过 PDFium 嵌入步骤，导致启动时出现
  `Library not loaded: @rpath/libpdfium.dylib` 的问题。

## 0.2.0 - 2026-07-28

本版本为 PDF Core 和 PDF Viewer 新增文档搜索支持。

### PDF Core

- Android、JVM、iOS、JavaScript 和 WasmJS 全平台支持文本搜索。
- 支持区分大小写、全词匹配和连续匹配选项。
- 每个搜索结果均提供文本范围和页面坐标区域。

### PDF Viewer

- 在 `PdfViewState` 中增加文档搜索状态和生命周期管理。
- 支持逐页更新搜索进度和结果，并提供选中、完成及失败状态。
- 支持选择上一个、下一个或指定搜索结果。
- 支持高亮全部搜索结果，并突出显示当前选中结果。
- 可通过 `PdfSearchHighlightStyle` 配置普通结果和当前结果的填充、描边、
  圆角及边距。
- 支持立即或平滑滚动到搜索词的准确位置，放大页面后也会调整横向位置。
- 支持配置搜索结果在视口内的对齐位置。
- Shared Demo 新增搜索测试页面。

## 0.1.0 - 2026-07-27

`pdf-core` 和 `pdf-viewer` 的首个公开版本。

### PDF Core

- 支持从字节数组打开 PDF，包括密码保护文档。
- 支持读取页数、PDF 版本、权限、元数据和页面标签。
- 支持读取页面尺寸、旋转方向和边界框。
- 支持将完整页面渲染为 BGRA8888 位图。
- 支持背景颜色、页面旋转、批注、灰度和 LCD 文本渲染配置。
- 支持基础文本提取。
- 支持内部页面链接、URI 链接及链接区域读取。
- Android 支持文本搜索。
- 提供文档、页面和位图的生命周期及异常处理 API。

### PDF Viewer

- 提供 Compose Multiplatform `PdfView` 页面查看组件。
- 支持纵向延迟加载和横向滚动。
- 支持双指缩放，并可配置最大缩放倍数。
- 支持关闭手势缩放，同时保留程序化缩放。
- 双指缩放期间禁用平移，减少页面抖动。
- 支持页面间距、内边距、颜色和边框配置。
- 支持限制页面渲染位图的最大尺寸。
- 支持页面加载、渲染错误和自定义错误 UI。
- 支持内部页面跳转和 URI 链接处理。
- 提供页面跳转、缩放控制和渲染缓存管理。

### 支持平台

- Android：arm32、arm64、x86、x64。
- iOS：真机 arm64、模拟器 arm64。
- JVM：macOS arm64/x64、Linux x64、Windows x64。
- 浏览器：JavaScript、Wasm。

### 当前限制

- 暂不支持书签、缩略图、表单和 PDF 编辑。
- 暂不支持随机访问输入源和局部裁剪渲染。
- Android 以外平台暂不支持文本搜索。
- Viewer 暂不支持文本选择、搜索结果高亮和分块渲染。
- 暂不支持 PDF JavaScript 和 XFA。
