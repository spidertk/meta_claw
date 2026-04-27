# LiteFlow 主路径清理与回归实施计划

> **给 agentic workers：** 必须使用子技能：优先使用 superpowers:subagent-driven-development，也可以使用 superpowers:executing-plans，按任务逐项执行本计划。步骤使用 checkbox（`- [ ]`）语法追踪进度。

**目标：** 删除已废弃的手写 application process 路径，让 LiteFlow facade/chain/node 成为唯一 application orchestration 路径，并完成 Java core 整体回归。

**架构：** `CoreController` 已经委托给 `KnowledgeFlowFacade`，facade 使用强类型 flow context 执行 LiteFlow chain。本计划删除无引用的旧 process 文件，更新当前模块 README 以反映 LiteFlow 主路径，并通过编译和 demo run 验证 Java core。

**技术栈：** Java 21、Gradle Kotlin DSL、Lombok、LiteFlow 2.12.0、Jackson、SLF4J simple runtime。

---

## 文件结构

- 删除：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/RegisterSourceProcess.java`
- 删除：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/ResumeSnapshotScanProcess.java`
- 删除：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/SubmitWorkerJobProcess.java`
- 删除：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/IngestWorkerResultProcess.java`
- 删除：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/ResolveKnowledgeSpaceBindingProcess.java`
- 修改：`knowledge/service/core/README.md`
- 验证：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/api/CoreController.java`
- 验证：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowFacade.java`
- 验证：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowExecutor.java`
- 验证：`knowledge/service/core/src/main/resources/liteflow/register-resume-el.xml`

## 任务 1：确认旧 Process 文件没有引用

**文件：**
- 检查：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/RegisterSourceProcess.java`
- 检查：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/ResumeSnapshotScanProcess.java`
- 检查：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/SubmitWorkerJobProcess.java`
- 检查：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/IngestWorkerResultProcess.java`
- 检查：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/ResolveKnowledgeSpaceBindingProcess.java`
- 检查：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/api/CoreController.java`
- 检查：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowFacade.java`

- [ ] **步骤 1：搜索旧 process 引用**

运行：

```bash
rg "RegisterSourceProcess|ResumeSnapshotScanProcess|SubmitWorkerJobProcess|IngestWorkerResultProcess|ResolveKnowledgeSpaceBindingProcess" knowledge/service/core/src/main/java
```

预期：只命中五个旧 process 文件中的 class 声明。如果其他 Java 源码仍引用这些类，先停止删除，并把调用方迁移到 `KnowledgeFlowFacade`。

- [ ] **步骤 2：确认 controller 只使用 facade**

运行：

```bash
sed -n '1,180p' knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/api/CoreController.java
```

预期：`CoreController` 只有一个构造依赖 `KnowledgeFlowFacade`，四个 public 方法构造强类型 flow context 后调用：

```java
knowledgeFlowFacade.registerSource(...)
knowledgeFlowFacade.resumeSnapshotScan(...)
knowledgeFlowFacade.submitWorkerJob(...)
knowledgeFlowFacade.ingestWorkerResult(...)
```

- [ ] **步骤 3：确认 facade 暴露四条主路径方法**

运行：

```bash
rg "public .* registerSource|public .* resumeSnapshotScan|public .* submitWorkerJob|public .* ingestWorkerResult" knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowFacade.java
```

预期：四条主路径各有一个 public 方法。

## 任务 2：删除旧 Process 文件

**文件：**
- 删除：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/RegisterSourceProcess.java`
- 删除：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/ResumeSnapshotScanProcess.java`
- 删除：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/SubmitWorkerJobProcess.java`
- 删除：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/IngestWorkerResultProcess.java`
- 删除：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/ResolveKnowledgeSpaceBindingProcess.java`

- [ ] **步骤 1：删除五个已废弃的 process 文件**

只删除以下文件：

```text
knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/RegisterSourceProcess.java
knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/ResumeSnapshotScanProcess.java
knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/SubmitWorkerJobProcess.java
knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/IngestWorkerResultProcess.java
knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/ResolveKnowledgeSpaceBindingProcess.java
```

- [ ] **步骤 2：重新搜索引用**

运行：

```bash
rg "RegisterSourceProcess|ResumeSnapshotScanProcess|SubmitWorkerJobProcess|IngestWorkerResultProcess|ResolveKnowledgeSpaceBindingProcess" knowledge/service/core/src/main/java
```

预期：无匹配。如果仍有匹配，说明存在陈旧引用，必须删除或迁移到 facade。

- [ ] **步骤 3：确认废弃 util 没有残留**

运行：

```bash
rg "SourceIntakeSupport" knowledge/service/core/src/main/java
```

预期：无匹配。不要重新创建 `SourceIntakeSupport`。

## 任务 3：更新 Java Core README

**文件：**
- 修改：`knowledge/service/core/README.md`

- [ ] **步骤 1：替换当前 scope 列表**

修改 `knowledge/service/core/README.md`，让 `Current scope:` 部分变为：

```markdown
Current scope:

- role-to-space resolution through the repository/facade main path
- domain records aligned with `knowledge/contracts`
- repository interfaces
- demo and JSONL file repository implementations
- LiteFlow facade + chain/node application orchestration
- internal worker transport models kept separate from external API requests
```

- [ ] **步骤 2：保持 path boundary 部分不变**

确认 `knowledge/service/core/README.md` 中仍存在以下内容：

```markdown
Path boundary:

- centralized shared knowledge root: `/meta_claw/knowledge_shared`
- private role spaces: external paths supplied by agent/runtime configuration
- this module does not own or generate private space directories
```

- [ ] **步骤 3：确认 README 不再暗示旧 use-case skeleton 是主路径**

运行：

```bash
rg "minimal use case|Process|process skeleton|use case" knowledge/service/core/README.md
```

预期：无匹配。

## 任务 4：编译回归

**文件：**
- 验证：`knowledge/service/core/build.gradle.kts`
- 验证：`knowledge/service/core/src/main/java`
- 验证：`knowledge/service/core/src/main/resources/liteflow/register-resume-el.xml`

- [ ] **步骤 1：运行 Java 编译**

在 `knowledge/service/core` 目录运行：

```bash
./gradlew classes
```

预期：构建成功，输出 `BUILD SUCCESSFUL`。

- [ ] **步骤 2：如果编译失败原因是已删除 process 被引用**

搜索失败符号：

```bash
rg "RegisterSourceProcess|ResumeSnapshotScanProcess|SubmitWorkerJobProcess|IngestWorkerResultProcess|ResolveKnowledgeSpaceBindingProcess" src/main/java
```

预期修复：删除陈旧引用，并让调用方走 `KnowledgeFlowFacade`。不要恢复已删除 process 作为 fallback。

- [ ] **步骤 3：如果编译失败原因是 LiteFlow node 缺少已迁移行为**

检查对应节点目录：

```text
knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register
knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume
knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/worker
```

预期修复：在 LiteFlow node 或 `KnowledgeFlowFacade` 中补齐缺失行为，然后重新运行 `./gradlew classes`。

## 任务 5：端到端 Demo 回归

**文件：**
- 验证：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/CoreApplication.java`
- 验证：`knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/KnowledgeFlowExecutor.java`
- 验证：`knowledge/service/core/src/main/resources/liteflow/register-resume-el.xml`
- 验证：`knowledge/demo-store/source-registry.jsonl`
- 验证：`knowledge/demo-store/snapshot-store.jsonl`

- [ ] **步骤 1：运行 demo 应用**

在 `knowledge/service/core` 目录运行：

```bash
./gradlew run
```

预期：命令成功退出，输出 `BUILD SUCCESSFUL`。

- [ ] **步骤 2：检查 source registration 输出**

预期日志包含类似内容：

```text
First registration -> source=..., snapshot=..., latestSnapshotId=..., scanStatus=..., nextScanCursor=..., unchanged=...
Second registration -> source=..., snapshot=..., latestSnapshotId=..., scanStatus=..., nextScanCursor=..., unchanged=...
```

由于 `knowledge/demo-store/*.jsonl` 可能包含之前运行的数据，具体 snapshot ID 和 unchanged 值可以不同。

- [ ] **步骤 3：必要时检查 resume 输出**

如果 first registration 输出 `scanStatus=partial`，预期日志包含：

```text
Resume scan -> snapshot=..., scanStatus=..., nextScanCursor=..., includedUnitCount=...
```

如果 first registration 已经 complete，则没有 resume 日志也可以接受。

- [ ] **步骤 4：检查 worker flow 输出**

预期日志包含类似内容：

```text
Submit worker job -> jobId=..., spaceId=..., snapshotId=..., jobType=...
Ingest worker result -> jobId=..., status=..., coverage=..., artifactCount=...
```

这些日志证明 `submitWorkerJobChain` 和 `ingestWorkerResultChain` 仍能通过 facade 访问。

## 任务 6：最终仓库检查与提交

**文件：**
- 提交：删除的 process 文件
- 提交：`knowledge/service/core/README.md`

- [ ] **步骤 1：检查最终 diff**

运行：

```bash
git diff -- knowledge/service/core/README.md knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application
```

预期：diff 只删除废弃 process 文件并更新 README 文案。除非回归失败要求修复主路径，否则 LiteFlow node、facade、domain、repository、contract 和 JSONL 格式不应改变。

- [ ] **步骤 2：检查本任务范围内的状态**

运行：

```bash
git status --short knowledge/service/core/README.md knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application
```

预期：能看到删除的旧 process 文件和修改后的 README。任务范围外可能已有其他工作区改动，不要回滚它们。

- [ ] **步骤 3：提交清理结果**

运行：

```bash
git add knowledge/service/core/README.md knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/RegisterSourceProcess.java knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/ResumeSnapshotScanProcess.java knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/SubmitWorkerJobProcess.java knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/IngestWorkerResultProcess.java knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/ResolveKnowledgeSpaceBindingProcess.java
git commit -m "refactor: remove legacy knowledge process path"
```

预期：提交成功。如果已有无关文件处于 staged 状态，先停止并检查 `git diff --cached --name-only`，不要把无关文件一起提交。

## 自检

- spec 覆盖：任务 1 和任务 2 覆盖旧 process 删除；任务 3 覆盖 README 修正；任务 4 和任务 5 覆盖编译与端到端回归；任务 6 覆盖最终限定范围提交。
- 占位扫描：没有未解决占位或含糊的实施步骤。
- 类型一致性：所有当前路径类名与已批准设计一致，包括 `CoreController`、`KnowledgeFlowFacade`、`KnowledgeFlowExecutor`、LiteFlow contexts、chain XML 和 application flow node packages。
