# 私有知识中台实现计划（重新制定）

日期：2026-04-27
关联设计文档：
- [`2026-04-20-private-knowledge-base-design.md`](./2026-04-20-private-knowledge-base-design.md)
- [`2026-04-20-private-knowledge-base-implementation-plan.md`](./2026-04-20-private-knowledge-base-implementation-plan.md)

## 1. 重新制定背景

原执行计划（2026-04-20）假设 Sprint 1/2 尚未开始，但截至今日代码已大幅演进：

- ✅ Java State Core 骨架已落地：LiteFlow 四条链、Source/Snapshot 领域模型、分批扫描、JSONL 持久化
- ✅ `SourceSnapshotScanner` 支持 content fingerprint、目录树 hash、分批 cursor
- ✅ `RegisterSourceChain` / `ResumeSnapshotScanChain` / `SubmitWorkerJobChain` / `IngestWorkerResultChain` 已可用
- 🟡 Python Worker 仍是 stub，只加载示例 JSON，未真实调用 graphify
- ❌ `.rwa` 来源规则未定义，当前 `CoreApplication` 仍使用硬编码 demo 数据
- ❌ loop-to-complete 调度尚未实现，超大目录需手动多次 resume
- ❌ 测试体系不存在（`src/test` 几乎空白）
- ❌ 无 REST API，只有本地 `main` 入口
- ❌ Knowledge State / Agent-facing Views 未落盘

因此，本计划**从代码事实出发**，忽略旧 Sprint 划分，重新梳理从当前状态到 Phase 1 最小闭环的推进路径。

## 2. 目标与范围

### 2.1 核心目标

建立最小可运行闭环：

```
.rwa 来源 → source_registry → snapshot_store (loop-to-complete)
  → Python Worker (graphify) → graph artifacts + wiki artifacts
  → Java 接收 artifact → knowledge_state → agent-facing views
```

### 2.2 范围边界

**包含：**
- `.rwa` 来源规则解析与扫描
- Loop-to-complete 分批调度
- Python Worker 真实调用 graphify
- Artifact 归档规范与落盘
- Knowledge State / Control State 最小落盘
- Agent-facing minimal views
- Spring Boot REST API（注册来源、提交 job、查询 view）
- JUnit 测试体系（单元、链路、集成、API）

**不包含（Phase 2）：**
- 四层 diff 精细增量
- `repo -> commit -> file/symbol` 增量链路
- `wiki_planner` 任务化
- lint / audit / retry / partial rebuild 自动化
- review gate 自动化
- 复杂检索与 topic routing

## 3. 推进策略：流式推进（Flow-by-Flow）

不采用"先补完 Java Core → 再做 API → 再做 Python"的层叠式，而是：

> **选定一条最小样本链路，从来源录入一路打通到 wiki 产出和 agent view，同步搭建测试和 API 脚手架。**

**具体节奏：**

| 阶段 | 时间 | 核心任务 |
|------|------|----------|
| Week 1 | 来源与扫描闭环 | 选定样本 + `.rwa` 规则 + loop-to-complete + 测试骨架 |
| Week 2 | Worker 真实调用 | Python Worker 接入 graphify + Artifact 归档 + 首条 graph/wiki |
| Week 3 | API 与集成 | Spring Boot REST + 端到端测试 + Agent views |
| Week 4 | 边界加固 | 失败路径保护 + 文档同步 + 样本扩展 |

**为什么选流式：**
- 最大风险是"Java 骨架和 Python Worker 之间的契约是否可行"，只有端到端跑通才能验证
- 测试和 API 作为"必要脚手架"同步搭建，而非前置阻塞

## 4. 架构概览

保持 **Java State Core + Python Worker Mesh** 双层架构：

```
┌─────────────────────────────────────────────────────────────┐
│  REST API (Spring Boot)                                     │
│  POST /sources, POST /jobs, GET /views                      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│  Java State Core                                            │
│  ├─ .rwa 来源规则解析器（RwaSourceResolver）                │
│  ├─ LiteFlow Chain: register → scan(loop-to-complete)       │
│  ├─ LiteFlow Chain: submitJob → dispatch → ingestResult     │
│  ├─ Repository: source_registry, snapshot_store             │
│  ├─ KnowledgeState 落盘（KnowledgeStatePersister）          │
│  └─ Agent-facing Views 生成器（AgentViewBuilder）           │
└─────────────────────────────────────────────────────────────┘
                            ↓  Job Contract (JSON)
┌─────────────────────────────────────────────────────────────┐
│  Python Worker Mesh                                         │
│  ├─ worker_entry.py: 解析 job，调用 graphify                │
│  ├─ graphify-out/ 产出 graph.json                           │
│  ├─ llm-wiki-md/ 产出 wiki.md                               │
│  └─ 返回 Artifact Contract (JSON)                           │
└─────────────────────────────────────────────────────────────┘
```

**新增 6 个核心组件：**
1. **`.rwa` 规则解析器 (`RwaSourceResolver`)** — 取代 `CoreApplication` 硬编码 demo 数据
2. **Loop-to-Complete 调度器 (`ScanCompletionScheduler`)** — 自动触发 `partial` snapshot 的后续批次
3. **Python Worker 调用器 (`GraphifyWorkerInvoker`)** — Java 侧组装命令行调用 Python 进程
4. **Artifact 归档器 (`ArtifactArchiveService`)** — 按 `space_id/source_id/snapshot_id/` 规范归档 Python 产出
5. **Knowledge State 落盘器 (`KnowledgeStatePersister`)** — 把 artifact 元数据写入主状态
6. **Agent View 生成器 (`AgentViewBuilder`)** — 按 role 过滤并组装消费视图

**引入 1 个基础设施层：**
- **Spring Boot REST 层** — 替换 `CoreApplication.main`，暴露 HTTP 端点

**不变原则：**
- Java 是唯一主状态持有者
- Python 只产出 artifact/result，不写主状态
- 所有状态更新带 `space_id + source_ref + snapshot_ref + job_id`

## 5. 组件职责边界

| 组件 | 职责 | 输入 | 输出 | 依赖 |
|------|------|------|------|------|
| **RwaSourceResolver** | 扫描 `.rwa/` 子目录，按约定生成 `SourceRegistrationRequest` | `.rwa/` 物理目录树 | `List<SourceRegistrationRequest>` | 文件系统 |
| **ScanCompletionScheduler** | 监听 `scanStatus=partial`，触发 resume 直到 `complete` | `SnapshotRecord` | 多次 `resumeSnapshotScan` 调用 | `SnapshotStoreRepository`, `KnowledgeFlowFacade` |
| **GraphifyWorkerInvoker** | 组装命令行调用 Python，捕获 stdout 返回 JSON | `WorkerJob` | `WorkerResultEnvelope` | 本地 Python、graphify |
| **ArtifactArchiveService** | 按规范目录归档 graph/wiki 文件，返回路径 | `WorkerResultEnvelope` + 原始文件 | 规范化 artifact 路径列表 | 文件系统 |
| **KnowledgeStatePersister** | 把 artifact 元数据写入 `knowledge_state`，初始化 `control_state=candidate` | `IngestWorkerResultFlowContext` | `KnowledgeStateRecord` | `KnowledgeStateRepository` |
| **AgentViewBuilder** | 按 role 过滤可见 space，组装 topic summary / source-backed context | `roleName` + `knowledge_state` | Markdown/JSON view | `KnowledgeStateRepository`, `KnowledgeSpaceBindingRepository` |

**关键边界：**
- Python **不直接写** `source_registry`、`snapshot_store`、`knowledge_state`
- Java **不直接解析** graphify 内部数据结构，只消费 artifact 元数据
- `ArtifactArchiveService` 是双方唯一的物理文件交接点

## 6. 端到端数据流（以单条样本链路为例）

以 `meta_claw` 自身或 `.rwa/graphify/` 作为**首条打通样本**：

### Step 1: 来源发现
`RwaSourceResolver` 扫描 `.rwa/` 目录，首期选 1 个子目录作为样本。

### Step 2: 来源注册
`POST /sources` → `ResolveKnowledgeSpaceNode` → `BuildSourceRecordNode` → `CreateSnapshotNode`

- `source_id` = SHA-256(规范化路径)
- 首份 `snapshot_id` = SHA-256(内容指纹 + 时间戳)
- `scanStatus` = `partial`（目录可能很大）
- `nextScanCursor` = 第一批次末尾位置

### Step 3: 分批扫描补全
`ScanCompletionScheduler` 检测到 `scanStatus=partial`：

```
while scanStatus != "complete":
    resumeSnapshotScan(source_id, nextScanCursor)
    → 生成下一批 UnitRef
    → 更新 nextScanCursor
    → 更新 scanStatus
```

### Step 4: 触发 Worker Job
```json
{
  "job_id": "job_graphify_001",
  "job_type": "extract_graph_and_wiki",
  "source_id": "src_xxx",
  "snapshot_id": "snap_yyy",
  "expected_artifacts": ["graph", "wiki"]
}
```

### Step 5: Python Worker 执行
`GraphifyWorkerInvoker` 组装命令行调用 `worker_entry.py`，Python 侧：
1. 读取 job contract
2. 调用 `graphify` 分析 snapshot 文件
3. 产出 `graph.json` + `wiki.md`
4. 按 Artifact Contract 返回 JSON

### Step 6: Artifact 归档
`ArtifactArchiveService` 把文件移入：

```
knowledge_shared/
  spaces/
    shared/
      sources/
        src_xxx/
          snapshots/
            snap_yyy/
              artifacts/
                job_graphify_001/
                  graph.json
                  wiki.md
```

### Step 7: 结果入仓
`IngestWorkerResultChain`:
- `ReadWorkerResultEnvelopeNode` → 解析 artifact 列表
- `IngestKnowledgeStateNode` → 写入 `knowledge_state`
  - `asset_type="graph"`, `status="ready"`, `control_state="candidate"`
  - `asset_type="wiki"`, `status="ready"`, `control_state="candidate"`

### Step 8: Agent 消费视图
`GET /views?role=codex_assistant` → `AgentViewBuilder`：
- 查询 role 绑定的 `space_id` 集合（own + inherited shared）
- 过滤 `knowledge_state` 中 `status=ready` 的 records
- 组装 Markdown view

## 7. 错误处理与状态保护

### 7.1 分层错误策略

| 层级 | 错误场景 | 处理策略 | 状态影响 |
|------|----------|----------|----------|
| **来源扫描** | 文件被删除/权限不足 | 跳过，记录 `issues`，继续扫描 | `scanStatus=partial`，cursor 跳过问题文件 |
| **Snapshot 生成** | fingerprint 计算失败 | 标记 `failed`，不写入 store | `latestSnapshotId` **不更新** |
| **Worker 调用** | Python 崩溃/超时 | 返回 `failed`，`retriable=true` | `knowledge_state` **不更新**，旧 artifact 可用 |
| **Worker 业务失败** | graphify 解析出错 | 返回 `failed`，`issues=[...]` | 只写入 `partial_artifacts`，标记 `needs_review` |
| **Artifact 归档** | 磁盘满/路径冲突 | 重试 3 次后失败 | 同 Worker 调用失败 |
| **结果入仓** | JSON 解析失败 | 记录日志，返回 400 | `knowledge_state` 不更新 |

### 7.2 核心不变式

1. **Snapshot 不可变性**：`snapshot_id` 一旦生成，内容永不修改
2. **Latest Snapshot 保护**：`latestSnapshotId` 只在**成功完成全量扫描**后更新
3. **Worker 失败不降级**：Python 失败时，Java 不自动降级到旧 artifact，但旧 artifact 仍可通过历史 `snapshot_id` 查询
4. **Partial Artifact 可追踪**：`partial_artifacts` 必须带完整 `space_id + source_id + snapshot_id + job_id`

### 7.3 重试机制

- **Worker 调用**：Java 侧最多 3 次，指数退避 1s → 2s → 4s
- **Snapshot 扫描 resume**：每次 resume 独立计算 cursor，失败不影响下一次
- **不无限重试**：3 次失败后标记 `failed`，需外部触发

## 8. 测试策略

测试与业务同步搭建，不是业务跑通后再补。

### 8.1 测试分层

| 层级 | 范围 | 工具 | 数量目标 |
|------|------|------|----------|
| **单元测试** | LiteFlow Node 单节点逻辑 | JUnit 5 + AssertJ | 每个 Node 1 正 + 1 败 |
| **链路测试** | 单条 Chain 完整执行（mock repository） | JUnit 5 + Mockito | 4 条 Chain 各 1 个 |
| **集成测试** | Java + 文件系统 + Python 真实调用 | JUnit 5 + `@TempDir` | 至少 2 个端到端样本 |
| **API 测试** | REST 端点穿透 | Spring Boot Test + MockMvc | 核心端点各 1 个 |

### 8.2 必测场景

**正向：**
1. 新来源注册 → snapshot → `latestSnapshotId` 更新
2. 重复注册 → 内容未变 → `unchanged=true`
3. 内容变化 → 新 snapshot → `latestSnapshotId` 更新
4. 大目录分批 → `partial` → resume → `complete`
5. Worker 成功 → artifact 归档 → `knowledge_state` 写入
6. Agent 按 role 查询 → 只返回 own + shared

**失败：**
7. Worker 崩溃 → `knowledge_state` 不更新，旧状态可用
8. 扫描中途失败 → `latestSnapshotId` 不回写
9. 来源路径不存在 → `failed`，不崩溃

### 8.3 样本测试数据

`src/test/resources/samples/` 下放置：
- `sample-repo/` — 微型 Python 项目（5~10 文件），测试 graphify 真实调用
- `sample-doc/` — Markdown + PDF，测试文档来源

样本复用：单元 fixture + 集成输入 + 演示素材

### 8.4 基础设施

- `build.gradle` 引入 `spring-boot-starter-test`、`mockito-core`、`assertj-core`
- 测试类标注 `@DisplayName`（中文描述意图）
- 本地 `./gradlew test` 30 秒内跑完

## 9. 4-Week 执行节奏

### Week 1: 来源与扫描闭环

**目标：** 让 `.rwa` 目录能自动进入系统，并自动完成分批扫描

**任务：**
- 定义 `.rwa` 目录与元数据规范（子目录如何映射为 source）
- 实现 `RwaSourceResolver`，取代 `CoreApplication` 硬编码数据
- 实现 `ScanCompletionScheduler`（loop-to-complete 调度）
- 搭建 JUnit 测试骨架 + 样本目录
- 测试覆盖：`registerSourceChain` 正向 + `unchanged` 判定 + 分批 resume

**完成标志：**
- 运行 `./gradlew test` 能通过注册 → 分批扫描 → complete 的测试
- `.rwa/graphify/` 能作为样本被自动注册

### Week 2: Worker 真实调用

**目标：** Python Worker 真正调用 graphify，产出 artifact

**任务：**
- 扩展 `worker_entry.py`：读取 job → 调用 graphify → 产出 `graph.json`
- 定义 artifact 归档目录规范
- 实现 `ArtifactArchiveService`
- 实现 `GraphifyWorkerInvoker`（Java 调用 Python 进程）
- 定义最小 wiki 模板，Python 侧产出 `wiki.md`
- 测试覆盖：Worker 成功路径 + artifact 归档可追溯

**完成标志：**
- 一个来源从注册到产出 `graph.json` + `wiki.md` 能手动跑通
- artifact 文件位于规范目录下

### Week 3: API 与集成

**目标：** 系统具备 REST 接口和端到端自动化测试

**任务：**
- 引入 Spring Boot，替换 `CoreApplication.main`
- 实现 `CoreController` REST 端点：`POST /sources`、`POST /jobs`、`GET /views`
- 实现 `KnowledgeStatePersister` 和 `AgentViewBuilder`
- 端到端集成测试：从 HTTP 注册到查询 view 全链路
- 测试覆盖：API 正向 + Worker 失败后旧状态保留

**完成标志：**
- `curl` 能完成注册 → 扫描 → job → view 全链路
- `./gradlew test` 包含至少 1 条端到端测试

### Week 4: 边界加固

**目标：** 失败路径有保护，文档与实现一致

**任务：**
- Worker 失败路径：`status=failed`、`retriable`、`partial_artifacts`
- Snapshot 扫描失败保护：`latestSnapshotId` 不回写
- 来源不存在保护：优雅失败
- 更新 schema 文档、状态流转图
- 扩展第 2 个样本来源（如文档类型）
- Spec 与实现对照，修正偏差

**完成标志：**
- 至少 2 个样本来源各完成一次闭环
- 失败场景测试全部通过
- schema / contract / 文档与代码一致

## 10. 约束与验收标准

### 10.1 状态主控约束

- 只有 Java 可以写：`.knowledge_registry.json`、`source_registry`、`snapshot_store`、`knowledge_state`
- Python 只能产出 artifact/result
- 任一状态更新必须带：`space_id`、`source_ref`、`snapshot_ref`、`job_id`

### 10.2 Worker 契约约束

- Python 只依赖公开 job contract
- Java 只依赖公开 artifact/result contract
- Worker 失败必须返回：`status`、`issues`、`retriable`、`partial_artifacts`

### 10.3 知识升级约束

- graph signal 默认是 `candidate`
- wiki 草稿默认是可审校投影
- 升级为 `active` 前必须具备：来源、coverage/scope、聚合依据

### 10.4 Artifact 约束

- graph、wiki、view 必须彼此可追踪
- 任一 artifact 必须能说明：所属 `space_id`、来源 source、来源 snapshot、生成 job、当前状态

### 10.5 DoD（每个任务）

- schema 或接口文档存在
- 至少一个真实样例存在
- 至少一个正向测试和一个失败场景测试存在
- 来源追踪关系能走通
- 文档同步到 spec / plan / schema 说明

### 10.6 验收总表

计划完成时必须满足：

- [ ] `.rwa` 来源能自动进入系统并生成 snapshot
- [ ] 分批扫描能自动完成到 `scanStatus=complete`
- [ ] Python Worker 能真实调用 graphify 产出 artifact
- [ ] Artifact 按规范目录归档且可追溯
- [ ] Java 能接收 artifact 并落成 `knowledge_state`
- [ ] Agent 能按 role 查询到稳定 views
- [ ] 至少 2 个样本来源完成端到端闭环
- [ ] Worker 失败后旧 knowledge state 仍可用
- [ ] `./gradlew test` 全部通过且 ≤30 秒
- [ ] REST API 可用 `curl` 完成全链路
- [ ] 文档与代码一致
