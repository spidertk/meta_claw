# Code-Authoritative Documentation Alignment Design

日期：2026-04-27

## 1. 背景

`knowledge/service/core` 经历了多轮重构，当前代码已经从手写 application process 主路径收敛到 LiteFlow facade + chain/node 主路径。

当前代码事实包括：

- `CoreController` 只依赖 `KnowledgeFlowFacade`
- 四条主链路由 LiteFlow 承接：
  - `registerSourceChain`
  - `resumeSnapshotScanChain`
  - `submitWorkerJobChain`
  - `ingestWorkerResultChain`
- 运行时依赖通过 `FlowRuntimeDependencies` 注入到 flow context
- 旧 `RegisterSourceProcess`、`ResumeSnapshotScanProcess`、`SubmitWorkerJobProcess`、`IngestWorkerResultProcess`、`ResolveKnowledgeSpaceBindingProcess` 已不再存在
- `SourceIntakeSupport` 已不再存在
- demo/file adapter 位于 `adapter/outbound/...`
- `knowledge/service/core/README.md` 已将当前主路径描述为 LiteFlow facade + chain/node application orchestration

但 `docs/superpowers/specs/` 和 `docs/superpowers/plans/` 中仍有多处旧描述。这些描述有些是历史背景，有些是当时的实施计划，有些看起来像当前状态。继续保留不加区分的旧描述，会让后续实现误以为旧 `*Process` 仍是当前主路径。

## 2. 目标

本轮目标是做一次以代码为权威事实源的全量文档校准：

- 扫描 `docs/superpowers/specs/` 和 `docs/superpowers/plans/` 下所有 Markdown 文档
- 修正文档中与当前代码不一致的旧 process、旧 adapter、旧主路径描述
- 对历史内容保留演进脉络，但明确标注“迁移前状态”“已被后续 LiteFlow 重构取代”或“不再适用”
- 对当前态和待执行计划直接改为当前代码事实
- 保证读者不会从 docs/superpowers 文档中误判旧 `*Process` 是当前可执行路径

## 3. 非目标

本轮不做以下事情：

- 不修改 Java 代码
- 不修改 contract schema
- 不修改 demo-store 数据
- 不删除历史 specs/plans
- 不把所有历史记录机械改成像从一开始就使用 LiteFlow

历史决策记录仍应保留，但必须和当前代码事实之间有清楚边界。

## 4. 文档校准原则

### 4.1 代码事实优先

当代码逻辑和文档不一致时，以当前代码为准修正文档。

当前权威代码入口包括：

- `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/api/CoreController.java`
- `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowFacade.java`
- `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowExecutor.java`
- `knowledge/service/core/src/main/resources/liteflow/register-resume-el.xml`
- `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/context/`
- `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/`
- `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume/`
- `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/worker/`

### 4.2 不做机械替换

不能简单把所有 `RegisterSourceProcess` 替换成某个 LiteFlow node 名称。

每处命中必须先判断语义：

- 如果是在描述迁移前背景，保留历史事实，并补充它已被后续 LiteFlow 主路径取代
- 如果是在描述当前状态，改为 `KnowledgeFlowFacade + LiteFlow chain/node`
- 如果是在描述未完成计划，改为已完成记录或不再适用说明
- 如果是在命令、路径或任务步骤里引用已不存在文件，改为当前文件路径或标注该步骤已过时

### 4.3 当前态描述必须一致

校准后，当前态描述必须统一为：

- controller 入口：`CoreController`
- application orchestration 入口：`KnowledgeFlowFacade`
- 编排执行：LiteFlow chain/node
- 运行时依赖载体：`FlowRuntimeDependencies`
- source intake 扫描逻辑：`SourceSnapshotScanner`
- adapter 位置：`adapter/outbound/demo` 和 `adapter/outbound/file`

## 5. 执行范围

必须扫描并按需修改：

- `docs/superpowers/specs/*.md`
- `docs/superpowers/plans/*.md`
- `knowledge/service/core/README.md`
- 必要时 `knowledge/README.md`

重点关键词包括：

- `RegisterSourceProcess`
- `ResumeSnapshotScanProcess`
- `SubmitWorkerJobProcess`
- `IngestWorkerResultProcess`
- `ResolveKnowledgeSpaceBindingProcess`
- `SourceIntakeSupport`
- `adapter/inbound`
- `sample repository`
- `minimal use case`
- `不迁 SubmitWorkerJobProcess`
- `不迁 IngestWorkerResultProcess`
- `只覆盖两条`
- `仍停留在旧的 *Process`

## 6. 处理规则

### 6.1 当前态文档

如果文档是在说明“当前实现”“已完成状态”“下一步基线”，必须直接改为当前代码事实。

示例方向：

- 从“当前仍以手写 `*Process` 编排为主”
- 改为“四条主链路已经统一走 `KnowledgeFlowFacade` 和 LiteFlow chain/node”

### 6.2 历史设计文档

如果文档是在说明当时为什么引入 LiteFlow，可以保留旧 process 作为历史背景，但必须加明确限定：

- “迁移前”
- “当时”
- “后续已被 LiteFlow 主路径取代”
- “该段为历史背景，不代表当前实现”

### 6.3 旧实施计划

如果旧计划中的步骤已经完成或不再适用：

- 已完成的步骤保留完成记录
- 不再适用的步骤标注为“不再适用，当前实现已迁移到 LiteFlow 主路径”
- 路径指向已不存在文件时，改为当前路径或标明该步骤是历史执行记录

## 7. 验证方式

### 7.1 文档一致性扫描

修改后重新运行关键词扫描。

允许旧 process 名称仍出现在历史段落中，但附近必须明确说明它是迁移前、历史、已取代或不再适用。当前态段落中不允许把旧 process 描述为可执行路径。

### 7.2 代码事实抽样核对

抽查以下文件，确认文档中的当前态描述与代码一致：

- `CoreController.java`
- `KnowledgeFlowFacade.java`
- `KnowledgeFlowExecutor.java`
- `register-resume-el.xml`
- flow context
- register/resume/worker node

### 7.3 回归验证

本轮不修改代码，但仍至少运行：

```bash
./gradlew classes
```

如果文档改动涉及 demo 行为说明，再运行：

```bash
./gradlew run
```

若回归失败，应记录为已有代码或环境问题，不通过修改文档掩盖。

## 8. 提交策略

提交分两步：

1. 先提交本设计文档
2. 后续 implementation plan 执行时，将文档校准作为独立提交

建议文档校准提交信息：

```bash
git commit -m "docs: align knowledge docs with liteflow code path"
```

## 9. 成功标准

本轮完成后：

- docs/superpowers 中不会再把旧 `*Process` 描述为当前主路径
- 旧 process 名称如果仍出现，必须带有历史或已取代语义
- 当前态文档与代码主路径一致
- `./gradlew classes` 通过
