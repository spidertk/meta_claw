# 多模态知识库扩展设计

**日期：** 2026-06-29  
**范围：** `meta-claw-tool` 知识库子系统 + `meta-claw-core` LLM SPI 轻量扩展  
**目标：** 让 `meta-claw-tool` 知识库支持图片、PDF、视频链接（优先抖音）作为知识来源，同时根据模型配置决定是否走原生多模态能力。

---

## 1. 背景与目标

### 1.1 现状

- `meta-claw-tool` 当前知识库只接受 `String content`，所有条目以 Markdown + YAML frontmatter 形式存在，由 `GitManager` 做版本化。
- 没有统一的媒体解析（Extractor）插件接口，也没有任何多模态支持。
- `meta-claw-core` 的 `SpiMessage` 只有 `String content`，不支持图片、音频、视频附件。

### 1.2 目标

1. **统一知识来源抽象**：无论是纯文本、图片、PDF 还是抖音视频链接，都通过同一套 `KnowledgeSource` 进入知识库。
2. **可插拔解析层**：新增一种媒体类型时，只需新增一个 `ContentExtractor` 实现。
3. **多模态感知**：根据当前模型配置判断 LLM 是否支持多模态；支持则优先把原图/原页面直接交给模型分析，不支持则走 OCR/描述/转录等文本化路径。
4. **保留现有 Git+Markdown 核心模型**：提取后的文本结论仍放在 `knowledge/` 下版本化，原始二进制放在 `assets/` 下不进入 Git LFS。

---

## 2. 设计原则

- **Source-in, Markdown-out**：`KnowledgeManager` 内部永远基于提取后的 Markdown/文本做分析、检索和存储。
- **多模态是分析阶段的可选项，不是存储强依赖**：即使走多模态分析，最终也要沉淀出可检索的文本摘要和元数据。
- **模型能力可配置**：通过配置项声明当前 LLM 是否支持多模态，系统据此选择分析路径。
- **最小侵入**：不破坏现有 `KnowledgeEntry`、frontmatter、Git 提交机制，只新增字段和扩展点。

---

## 3. 核心抽象与数据模型

### 3.1 KnowledgeSource

表示任意知识来源的统一入口。

```java
@Data
@Builder
public class KnowledgeSource {
    private String sourceId;           // 可选，外部传入时由系统生成
    private String mediaType;          // text/plain, image/png, application/pdf, video/url.douyin ...
    private URI uri;                   // 本地路径或远程 URL
    private InputStream stream;        // 内联字节流
    private String originalName;       // 原始文件名或标题
    private String content;            // text/plain 时的快捷文本内容
    private Map<String, Object> extra; // 来源扩展信息，如分享文案、视频链接参数等
}
```

### 3.2 ContentExtractor SPI

```java
public interface ContentExtractor {
    boolean supports(KnowledgeSource source);
    ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx);
}
```

实现类作为 Spring `@Component` 注册，由 `ContentExtractorService` 自动收集并路由。

### 3.3 ExtractedDocument

解析产物，包含给 LLM 分析的文本，以及可选的嵌入式媒体资源。

```java
@Data
@Builder
public class ExtractedDocument {
    private String markdownBody;       // 文本化结论，进入 KnowledgeAnalyzer
    private String mediaType;          // 来源媒体类型
    private List<AssetRef> embeddedAssets; // 图片、PDF 页、视频缩略图等
    private Map<String, Object> metadata;  // 页数、时长、分辨率、原始链接等
}
```

### 3.4 AssetManager

负责把原始二进制保存到 `.meta-claw/vessels/{vesselId}/assets/{assetId}/`，并返回 `AssetRef`。

```java
public interface AssetManager {
    AssetRef store(KnowledgeSource source);
    InputStream load(AssetRef ref);
    Path resolvePath(AssetRef ref);
}
```

**存储约定**：

```text
.meta-claw/vessels/{vesselId}/
├── knowledge/                         ← Git 版本化（只放文本/元数据）
│   └── {topic}/
│       └── {title}.md
└── assets/                            ← 不进入 Git LFS，也不强制版本化
    └── {assetId}/
        ├── original.png
        ├── extracted.md
        ├── meta.json
        └── pages/
            ├── page_01.png
            └── page_01_ocr.md
```

### 3.5 KnowledgeEntry 扩展

在现有 frontmatter 基础上新增字段：

```yaml
---
id: a1b2c3d4
title: 抖音视频摘要
type: fact
status: active
topics:
  - short_video
source_asset: assets/{assetId}/original.mp4   # 原始文件引用
extracted_asset: assets/{assetId}/extracted.md # 提取文本
media_type: video/url.douyin                  # 媒体类型
multimodal_used: true                         # 是否使用了多模态分析
---
```

---

## 4. 多模态支持设计

### 4.1 模型能力配置

新增配置类 `MultimodalConfig`：

```yaml
meta-claw:
  llm:
    multimodal:
      enabled: true                       # 当前模型是否支持多模态
      supported-media-types: image/png, image/jpeg, image/webp
      max-image-size: 5MB
      supported-pdf-mode: per-page-image  # none | text-only | per-page-image
```

或更简单地，在 `LlmClient` 配置里加一个布尔属性：

```yaml
meta-claw:
  llm:
    client:
      supports-vision: true
```

### 4.2 ModelCapability 接口

```java
public interface ModelCapability {
    boolean supportsMultimodal();
    boolean supportsMediaType(String mediaType);
    boolean supportsPdfPageImages();
}
```

`KnowledgeAnalyzer` 依赖 `ModelCapability` 做决策。

### 4.3 多模态决策逻辑

在 `KnowledgeAnalyzer.analyze(...)` 内部：

```java
public AnalysisResult analyze(ExtractedDocument doc, List<KnowledgeEntry> related, String context) {
    if (modelCapability.supportsMultimodal() && hasVisualAssets(doc)) {
        return analyzeWithMultimodal(doc, related, context);
    } else {
        return analyzeWithText(doc.getMarkdownBody(), related, context);
    }
}
```

### 4.4 多模态分析路径

当模型支持多模态时：

1. `KnowledgeAnalyzer` 构造包含图片/页面附件的 prompt。
2. 需要 `SpiMessage` / `SpiChatRequest` 支持媒体内容（见第 7 节）。
3. LLM 直接看原图或 PDF 页面图片，输出分类、摘要、矛盾检测等结果。
4. 最终仍把 LLM 返回的文本摘要写入 `KnowledgeEntry.content`。

当模型不支持多模态时：

1. `ImageExtractor` / `PdfExtractor` 预先 OCR 或生成描述。
2. `KnowledgeAnalyzer` 只接收文本，走现有分析流程。

---

## 5. 摄入流程

```text
KnowledgeSource (text/image/pdf/url)
        ↓
AssetManager.store(source) → assetId + 原始文件
        ↓
ContentExtractorService.route(source)
        ↓
ContentExtractor.extract(source, ctx)
        ↓
ExtractedDocument
        ↓
KnowledgeAnalyzer.analyze(doc, related, context)
  ├─ 多模态模型 → 直接看图/页面分析
  └─ 非多模态 → 用 markdownBody 分析
        ↓
KnowledgeEntry (引用 source_asset, extracted_asset)
        ↓
GitManager.commitKnowledge(filePath, message)
```

`KnowledgeManager.acquire` 新签名：

```java
public Map<String, Object> acquire(KnowledgeSource source, String context, boolean dryRun)
```

纯文本也统一包装成 `KnowledgeSource(mediaType="text/plain", content=...)`，由 `TextExtractor` 处理。

---

## 6. 检索流程

1. `GitManager.grepFiles` 搜索范围从 `knowledge/**/*.md` 扩展为同时包含 `assets/**/extracted.md`。
2. 加载 `KnowledgeEntry` 后，返回结果里增加 `source_asset` 和 `media_type` 字段。
3. 如果消费端模型支持多模态，可读取 `source_asset` 中的图片/PDF 页面原图参与回答；否则只使用文本摘要。

---

## 7. meta-claw-core LLM SPI 扩展

当前 `SpiMessage` 只有 `String content`，无法携带图片。需要增加媒体内容支持：

```java
@Data
@Builder
public class SpiMessage {
    private String role;
    private String content;
    private List<MediaPart> mediaParts;  // 新增
    ...
}

@Data
@Builder
public class MediaPart {
    private String type;      // image_url, image_base64, audio_url ...
    private String url;       // 本地 asset URL 或远程 URL
    private String mimeType;  // image/png
    private byte[] data;      // 内联二进制
}
```

底层引擎（如 `SpringAiAlibabaAgentEngine`）负责把 `MediaPart` 转换为对应模型 SDK 的多模态消息格式。

> 这是一个跨层改动，需要单独一个子任务完成，但对知识库设计是必要前提。
>
> **关于流式返回：** 流式（Streaming）与多模态输入是两个独立维度。`MediaPart` 只用于请求输入；模型无论是否“看到”图片，最终输出仍是文本流。因此 `SpiStreamingCallback` 的返回结构暂不需要同步修改，等基础链路跑通后再按需优化。

---

## 8. 各媒体类型的 Extractor 实现

### 8.1 TextExtractor

处理 `text/plain`。

- 直接把 `source.content` 作为 `markdownBody`。

### 8.2 ImageExtractor

处理 `image/*`。

- 总是保存原图到 `assets/{assetId}/original.{ext}`。
- 如果当前模型**不支持多模态**，调用 vision 模型生成描述文本作为 `markdownBody`。
- 如果当前模型**支持多模态**，`markdownBody` 可置为简短文件名/提示，由 `KnowledgeAnalyzer` 直接读取原图分析。

### 8.3 PdfExtractor

处理 `application/pdf`。

- 保存原文件 `original.pdf`。
- 文本提取：PDFBox / Tika 提取文字 → `extracted.md`。
- 扫描页 OCR：逐页渲染为图片 → `ImageExtractor` → 每页 OCR 文本。
- 多模态模式：逐页图片交给 LLM 做页面级理解。
- 嵌入式图片：可选提取后调用 `ImageExtractor` 描述。

### 8.4 DouyinVideoExtractor

处理 `video/url.douyin`。

- 解析抖音分享链接，提取视频 ID。
- 优先获取平台字幕/文案；无字幕则下载音频并调用 Speech-to-Text。
- **实现策略：** 定义 `VideoExtractor` 接口，默认提供基于 `yt-dlp` 的 `YtDlpVideoExtractor` 适配器。`yt-dlp` 作为可选/实验性依赖，文档中明确环境要求，并设计降级策略（如未安装时返回错误提示或仅提取链接文案）。未来出现官方 API 或更稳定的纯 Java 方案时，可无缝替换。
- 产物：
  - `original.mp4`（或仅音频，取决于策略）
  - `extracted.md`：标题 + 摘要 + 时间戳 transcript
  - `thumbnail.jpg`
  - `meta.json`：作者、点赞数、发布时间、原始链接等

安全与限制：

- URL 域名白名单（`douyin.com`、`iesdouyin.com` 等）。
- 最大下载时长/大小。
- 第一版采用**同步提取 + 超时控制**（如 30 秒），优先覆盖 1-3 分钟短视频；异步任务队列作为后续优化。

---

## 9. Tool 接口

保留现有 `knowledgeAcquire(String content, ...)` 以兼容旧调用，内部包装为 `KnowledgeSource`。

新增来源型入口：

```java
@Tool(description = "Acquire knowledge from a local file (image, PDF, etc.)")
public String knowledgeAcquireFromFile(
    @ToolParam(description = "Absolute or vessel-relative file path") String filePath,
    @ToolParam(description = "Optional context") String context,
    @ToolParam(description = "Dry run") Boolean dryRun)

@Tool(description = "Acquire knowledge from a URL (currently Douyin prioritized)")
public String knowledgeAcquireFromUrl(
    @ToolParam(description = "Source URL") String url,
    @ToolParam(description = "Optional context") String context,
    @ToolParam(description = "Dry run") Boolean dryRun)
```

两个方法最终都调用 `KnowledgeManager.acquire(KnowledgeSource, context, dryRun)`。

---

## 10. 安全与限制

- 文件类型白名单：`image/*`, `application/pdf`, `video/url.douyin`。
- 远程下载限制：URL 白名单、超时、最大大小、最大时长。
- 原始文件不进入 Git，避免仓库膨胀；必要时后续可接对象存储。
- 所有 extractor 运行在工具调用线程，大文件/长视频需要异步任务支持。

---

## 11. 实现阶段

### 阶段 1：SPI 骨架 + TextExtractor

- 新增 `KnowledgeSource`, `ExtractedDocument`, `ContentExtractor`, `ContentExtractorService`, `AssetManager`, `AssetRef`。
- 改造 `KnowledgeManager.acquire(KnowledgeSource, ...)`。
- 实现 `TextExtractor`，保证现有纯文本测试全部通过。

### 阶段 2：ImageExtractor + 多模态基础

- 扩展 `SpiMessage` 支持 `MediaPart`。
- 实现 `ImageExtractor` 和 vision 描述 fallback。
- 新增 `MultimodalConfig` / `ModelCapability`。
- `KnowledgeAnalyzer` 根据模型能力选择多模态或文本路径。
- 新增 `knowledgeAcquireFromFile`。

### 阶段 3：PdfExtractor

- 集成 PDFBox / Tika。
- 支持文本 PDF、扫描 PDF OCR、多模态逐页分析。

### 阶段 4：DouyinVideoExtractor

- 抖音链接解析、字幕/音频提取、STT。
- 新增 `knowledgeAcquireFromUrl`。
- 同步提取并设置超时（如 30 秒），先支持 1-3 分钟短视频；异步作为后续优化。

### 阶段 5：检索增强

- `GitManager.grepFiles` 扩展搜索 `assets/**/extracted.md`。
- 检索结果返回 `source_asset` / `media_type`。

---

## 12. 关键决策记录

| 决策 | 选择 | 理由 |
|---|---|---|
| Manager 入口 | `KnowledgeSource` 单一入口 | 取消兼容 `String content` 旧签名，内部流程更统一 |
| 原始文件版本化 | 不进入 Git，放 `assets/` | 避免仓库膨胀，先不用 Git LFS |
| 多模态开关 | 配置驱动 `ModelCapability` | 根据当前模型能力动态选择分析路径 |
| 视频优先平台 | 抖音 | 业务优先级 |
| 远程下载 | 白名单 + 大小/时长限制 | 安全 |

---

## 13. 已确认决策

| 问题 | 决策 |
|---|---|
| `SpiMessage` 多模态扩展是否同步改流式返回结构？ | **否**。`MediaPart` 仅用于请求输入，流式输出仍为文本，返回结构不变。 |
| 抖音解析是否允许外部可执行程序？ | **允许**，但 `yt-dlp` 作为可选/实验性依赖，通过 `VideoExtractor` 接口隔离，文档说明环境依赖并设计降级策略。 |
| 视频提取第一版是否异步？ | **否**。第一版采用同步 + 超时控制（30 秒），覆盖短视频；异步作为后续优化。 |
