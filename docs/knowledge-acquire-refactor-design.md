# 知识采集链路技术改造方案（knowledgeAcquireFromFile）

> 日期：2026-07-20
> 状态：Phase 1+2+3 已实现（经用户评审确认：分析调用默认不附原图、非 CLI 通道走异步 pending 提案）
> 范围：`meta-claw-core` knowledge 包、`meta-claw-tool` KnowledgeTool、`meta-claw-cli` HITL 交互

## 1. 现状与问题（代码核实结论）

### 1.1 当前调用链（图片问询场景，4 次大模型调用）

```
KnowledgeTool.knowledgeAcquireFromFile            meta-claw-tool/.../KnowledgeTool.java:64
 └─ KnowledgeManager.acquire()                    meta-claw-core/.../knowledge/KnowledgeManager.java:69
     ├─ AssetManager.store()                      L77   ← 存资产（第 1 份）
     ├─ ImageExtractor.extract()                  extract/ImageExtractor.java:28
     │    ├─ assetManager.store()                 L29   ← 又存一次资产（第 2 份，assetId 不同）
     │    └─ LLM ① VisionDescriber.describe()     extract/VisionDescriber.java:24
     ├─ LLM ② analyzer.extractKeywords()          KnowledgeManager.java:84
     ├─ findRelatedEntries()                      L87   ← 非 LLM，git grep 关键词
     └─ LLM ③ analyzer.analyze()                  KnowledgeManager.java:90
          └─ 多模态路径下图片又被附带一次（analyzeWithMultimodal, KnowledgeAnalyzer.java:87）
```

PDF 场景更糟：`PdfExtractor` 每页渲染一张 PNG 各调一次 vision（页数 = 调用次数），之后同样再走 ②③。

### 1.2 三个核心问题

| # | 问题 | 根因 |
|---|------|------|
| P1 | 模型调用过多、图片重复上传 | ① 的图像描述与 ③ 的多模态分析各发一次图；② 的关键词提取完全冗余——③ 的分析 prompt 输出里已包含 `extracted_keywords`；检索（git grep）本不需要 LLM，却被夹在两次调用之间拆散了流水线 |
| P2 | `needs_review` 不走 HITL | `KnowledgeManager.acquire()` L103-121：置信度 < 0.9 或有矛盾时返回 `status=needs_review` 文本，**不落盘也不等用户**，知识被静默丢弃；现有 HITL 子系统（`HitlSubSystem`/`CliHitlGate`）只按**工具名**在执行前拦截，看不到工具返回值 |
| P3 | 资产重复录入 | `LocalAssetManager.store()` 无内容 hash、无 asset 索引，assetId 用随机 UUID；且同一次 acquire 在 `KnowledgeManager:77` 和 `ImageExtractor:29` **存了两份 assetId 不同的副本** |

## 2. 业界实践参照

- **LightRAG / GraphRAG 增量索引**：新文档先经内容级去重（只处理增量），实体合并（dedup）在写入前完成，避免图谱污染与重复计算。
- **Agentic Write Layer（LightRAG Discussion #2999）**：把"写入前校验"做成 reasoning-before-insertion——先查已有上下文，由模型在一次推理里决定 add/merge/reject，而不是盲目 upsert 后清洗。
- **HITL generate-then-refine**：知识构建流水线普遍采用"模型生成结构化提案 → 人审 → 确认入库"两段式，人审是持久化的 review queue 而非一次性对话。
- **content-addressable 存储**：资产以内容 hash 为键（Git blob、IPFS、Unstructured 等均如此），天然幂等去重。

## 3. 目标流程

```
用户发起图片/文件问询
  │
  ▼
[0] 程序判定（零 LLM 调用）
    sha256(file) → 查 AssetRegistry
    ├─ 命中 → 直接返回该资产已提炼的知识条目（status=already_known），流程结束
    └─ 未命中 → 继续
  │
  ▼
[1] 资产入库（仅一次） assetId = sha256 前 12 位，幂等
  │
  ▼
[2] LLM 调用 ①：视觉理解 + 关键词（合并为一个 prompt）
    输出：description + keywords + ocr_text
  │
  ▼
[3] 程序检索（零 LLM）：git grep 关键词 → 相关知识条目
  │
  ▼
[4] LLM 调用 ②：统一知识分析（文本即可，不必再发图；多模态模型可选附带）
    输入：① 的描述 + ③ 的相关条目 + context
    输出（单一 JSON）：knowledge_type / title / topics / keywords /
    contradiction / confidence / recommended_action / commit_summary / 正文 markdown
  │
  ▼
[5] HITL 人审（必经，替代 confidenceThreshold 自动落盘）
    向用户展示结构化提案预览（标题/类型/topics/矛盾说明/建议动作/正文摘要）
    ├─ 确认 → executeAcquire：写 md + 标记被替代条目 SUPERSEDED + git commit + 登记 AssetRegistry
    ├─ 拒绝 → 丢弃提案，记录 review 日志，资产保留
    └─ 修改（可选）→ 用户给一句话修正意见，追加进 prompt 重跑 [4] 一次
```

LLM 调用次数：图片 4 → 2；N 页 PDF N+2 → 2（页图合并为一次多图调用，或先 PDFBox 抽文本、仅无文本页才发图）。

## 4. 分阶段实施

### Phase 1 — 资产去重与单次入库（纯程序逻辑，零风险）

1. 新增 `AssetRegistry`（`knowledge/asset/` 包）：
   - 持久化 `.meta-claw/vessels/{vesselId}/assets/index.json`：`sha256 → {assetId, mediaType, knowledgeEntryIds[], createdAt}`。
   - `@Component`，`@Builder` DTO，读写加锁。
2. `LocalAssetManager.store()` 改为 content-addressable：
   - 先算 sha256 → 查 `AssetRegistry`，命中直接返回已有 `AssetRef`（带 `alreadyExists=true`）；
   - 未命中用 hash 前 12 位作 assetId 落盘并登记。
3. 修双存 bug：删除 `ImageExtractor.extract()` L29 / `PdfExtractor.extract()` L41 的重复 `store()`，统一由 `KnowledgeManager.acquire()` 一处调用，`ExtractedDocument` 复用同一 `AssetRef`。
4. `KnowledgeManager.acquire()` 开头加快捷路径：资产已存在且已有 `knowledgeEntryIds` → 直接返回 `{status: "already_known", entries: [...]}`，零 LLM 调用。
   - 保留 `forceReanalyze` 参数（默认 false）允许显式强制重提炼。

**验证**：同一图片连续 acquire 两次，第二次无 LLM 调用、assets 目录无新增、返回已有条目。

### Phase 2 — 合并模型调用（4 → 2）

1. `VisionDescriber.describe()` 扩展为 `VisionUnderstandingService.analyze()`：
   - 一个 prompt 同时输出 `description` + `keywords[]` + `ocr_text`（JSON）。
   - PDF：页图合并为一次多 `MediaPart` 调用（上限沿用 5 张，超出分批）；有文本层的页直接抽文本不发图。
2. 删除独立的关键词调用 `KnowledgeAnalyzer.extractKeywords()`（KnowledgeManager.java:84），关键词由 ① 提供；`findRelatedEntries` 不变。
3. `KnowledgeAnalyzer.analyze()` 默认改为文本输入（吃 ① 的结构化描述），不再重复附带图片；`MultimodalConfig` 增加 `attachImageToAnalysis` 开关（默认 false）供多模态模型兜底。
4. （可选，独立小步）`LlmClientManager` 引入 purpose 维度：关键词/描述类调用可路由到便宜模型。当前合并后只剩 2 次调用，优先级低，可暂缓。

**验证**：`KnowledgeAcquisitionSmokeTest` 断言 LLM 调用次数 = 2（mock `SpiLlmClient` 计数）。

### Phase 3 — HITL 审核入库（核心行为变更）

1. `KnowledgeManager.acquire()` 拆为两段：
   - `propose(source, context)` → 返回 `KnowledgeProposal`（`@Builder` DTO：assetRef、分析 JSON、渲染好的预览文本、相关条目、待 supersede 列表），**不写盘**；
   - `commit(proposal)` → 现有 `executeAcquire` 逻辑 + 补上做 `KnowledgeEntry.supersede()` 改写旧条目状态（当前缺失）。
2. HITL 接入（复用现有基建，不新造轮子）：
   - 新增 `KnowledgeReviewGate` 接口 + CLI 实现 `CliKnowledgeReviewGate`（复用 `TerminalConfig` 的 JLine `LineReader`，仿 `CliHitlGate`）：打印提案预览，问 `确认入库? (Y/n/e[dit])`。
   - 注入到 `KnowledgeTool.knowledgeAcquireFromFile`：`propose()` 之后同步调 gate；确认才 `commit()`。
   - `dryRun=true` 时跳过 gate 直接返回提案。2026-07-21 语义更新：dryRun 只做内容提取（如图片识别），跳过关键词/矛盾自检等分析 LLM 调用，返回 `status=extracted`；提案以 `analysis=null` 持久化，approve 时在 `commitProposal` 补跑一次统一分析后落库，无需重新提取。
   - 置信度阈值 `confidenceThreshold` 从"自动落盘开关"降级为"预览中的风险提示"（< 0.9 时预览标注 ⚠️），**入库一律经人确认**——这正是用户要求的行为。
3. 非 CLI 通道（gateway/weixin）：gate 实现为异步挂起——提案持久化到 `knowledge/.pending/{proposalId}.json`，工具返回 `status=pending_review + proposalId`；新增 `knowledgeReview(proposalId, decision)` 工具让用户后续确认/拒绝。CLI 也可复用该持久化，避免进程中断丢提案。
4. 主 agent loop 无需改动：提案预览和审核结果都通过工具返回值自然呈现给用户。

**验证**：CLI 端到端——发起图片问询 → 控制台出现结构化提案 → 输入 n 不落盘；输入 y 后 knowledge 目录出现新 md、被替代条目状态变为 superseded、git log 有新 commit、index.json 登记完整。

### Phase 4 —（后续可选）

- purpose 级模型路由（便宜模型做视觉描述）；
- review 日志与提案统计（采纳率反哺 prompt 调优）；
- 语义级去重（embedding 近邻检测"换皮"资产），当前 hash 只挡字节级重复。

## 5. 影响面与兼容性

| 改动 | 文件 | 兼容性 |
|------|------|--------|
| AssetRegistry + content hash | `LocalAssetManager`、新增 `AssetRegistry` | 存量 assets 无 index.json，首次访问惰性补登记 |
| 删重复 store | `ImageExtractor`/`PdfExtractor` | `ExtractedDocument` 的 asset 引用与结果 `asset_id` 变一致（修 bug） |
| 合并 LLM 调用 | `VisionDescriber`、`KnowledgeAnalyzer`、`KnowledgeManager` | prompt 输出 schema 需保持 `KnowledgeAnalysis` 字段不变 |
| propose/commit 拆分 | `KnowledgeManager`、`KnowledgeTool` | 工具返回值结构变化：新增 `pending_review`/`already_known` 状态；`KnowledgeTool.formatAcquireResult` 同步更新 |
| HITL gate | 新增 `KnowledgeReviewGate` + CLI 实现 | 非 CLI 通道走 pending 提案，行为降级为"返回待审"而非阻塞 |

## 6. 风险

- **prompt 合并后单次输出质量**：描述+关键词合一、分析与描述分离后，② 看不到原图（默认文本路径）可能损失细节 → 用 `attachImageToAnalysis` 开关兜底，A/B 对比冒烟测试。
- **同步 gate 阻塞 agent loop**：CLI 单线程下可接受；gateway 通道必须用异步 pending 方案，不能复用阻塞实现。
- **index.json 并发**：多 vessel 并发 acquire 需文件锁或单写者约束（首版单进程内存锁即可）。
