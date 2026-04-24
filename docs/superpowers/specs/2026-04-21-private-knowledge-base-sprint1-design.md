# 私有知识中台 Sprint 1 设计

日期：2026-04-21
关联文档：

- [`2026-04-20-private-knowledge-base-design.md`](./2026-04-20-private-knowledge-base-design.md)
- [`2026-04-20-private-knowledge-base-implementation-plan.md`](./2026-04-20-private-knowledge-base-implementation-plan.md)

## 1. 范围

本设计只覆盖 `Sprint 1：状态骨架和契约骨架`。

本轮目标是固定后续不应轻易推翻的边界：

- 共享 schema
- `space_id` 隔离模型
- `.knowledge_registry.json` 注册入口
- 状态流转
- Java 主状态模型骨架
- Python worker 对接骨架
- 最小端到端假链路

本轮明确不做：

- 真实 ingest
- 真实 `graphify` 调用
- 真实 wiki 生成
- 真实 diff 计算
- 复杂 retrieval
- 复杂 topic routing

## 2. 目标

`Sprint 1` 完成后必须满足：

- Java 与 Python 之间通过稳定 contract 交互
- Java 是唯一主状态持有者
- Python 只返回 artifact/result
- 所有核心状态都能用语言无关 schema 表达
- 可以跑通一条“假执行、真状态更新”的端到端链路

## 3. 目录设计

本轮固定使用以下目录结构：

```text
meta_claw/
  knowledge/
    contracts/
    examples/
    service/
      core/
    workers/
      python/
  knowledge_shared/
    <shared_space_id>/
  .knowledge_registry.json
```

约束如下：

- `.knowledge_registry.json`
  是角色到 external knowledge space 的唯一注册入口
- `knowledge/service/core`
  代表 `Java State Core` 的实现目录
- `knowledge/workers/python`
  代表 Python worker 目录
- `knowledge/contracts`
  存放语言无关 schema
- `knowledge/examples`
  存放共享样例
- `knowledge_shared`
  是集中维护的 shared knowledge 根目录，不属于 core 代码结构
- private `spaces`
  由外部 agent/runtime 配置提供路径，core 只读取与处理

本轮不引入第二个 Java 根目录，也不把 Python worker 混入 Java 服务目录。

## 4. 共享 Contract

本轮先定义以下 contract：

- `knowledge-registry.schema.json`
- `unit-ref.schema.json`
- `source-registry.schema.json`
- `snapshot-store.schema.json`
- `knowledge-assets.schema.json`
- `knowledge-control-state.schema.json`
- `job-contract.schema.json`
- `artifact-result.schema.json`

要求：

- Java DTO 与 Python 输出结构都以这些 contract 为准
- contract 必须先于 Java model 和 Python stub 落地
- contract 允许扩展字段区，但不允许模糊字段名

## 5. 状态模型

### 5.1 来源处理状态

本轮固定以下最小状态：

- `new_source`
- `unchanged`
- `partial_update`
- `major_update`
- `failed`

### 5.2 知识生命周期

本轮固定以下最小状态：

- `candidate`
- `active`
- `needs_review`
- `stale`
- `superseded`
- `rejected`

### 5.3 主控约束

- 只有 Java 能修改：
  - `.knowledge_registry.json`
  - `source_registry`
    负责来源稳定身份、当前状态和 `latest_snapshot_id`
  - `snapshot_store`
    负责按 `source_id` 关联的不可变快照历史
  - `knowledge_state`
  - `knowledge_control_state`
- Python 只能返回：
  - `status`
  - `artifacts`
  - `issues`
  - `coverage`
  - `scope`
  - `retriable`

## 6. Java 骨架

`knowledge/service/core` 本轮只落以下骨架。

### 6.1 Domain

- `KnowledgeSpace`
- `AgentRoleBinding`
- `SourceRecord`
- `SnapshotRecord`
- `UnitRef`
- `KnowledgeAsset`
- `KnowledgeControlState`
- `WorkerJob`
- `WorkerResult`

要求：

- 所有核心状态必须带 `space_id`
- 字段与 contract 一一对应
- 不引入 contract 外的核心语义字段
- 先使用内存 repository stub
- 本轮所有新增和修改代码都必须补齐职责清晰的中文注释

### 6.2 Application

本轮只定义 4 个 application process：

- `ResolveRoleBindingProcess`
- `RegisterSourceProcess`
- `SubmitWorkerJobProcess`
- `IngestWorkerResultProcess`

用途：

- 根据 role 解析 `space_id`
- 注册来源
- 生成 worker job
- ingest worker result 并更新状态

### 6.3 API

本轮 API 只要求 DTO 和最小入口骨架，不要求完整服务能力。

## 7. Python 骨架

`knowledge/workers/python` 本轮只做最小 stub。

包含：

- `README.md`
- `contracts/`
- `examples/`
- 一个最小入口文件，例如 `worker_entry.py`

行为：

- 读取 `job-contract.example.json`
- 输出成功或失败的 result 示例

本轮禁止接真实 `graphify`。

## 8. 样例

本轮至少需要以下样例：

- `.knowledge_registry.json`
- `source-registry.example.json`
- `snapshot-store.example.json`
- `job-contract.example.json`
- `artifact-success.example.json`
- `artifact-failure.example.json`

这些样例的用途是：

- 校验 contract
- 校验 Java DTO
- 校验 Python stub
- 支撑最小端到端假链路

## 9. 最小端到端链路

本轮必须跑通一条假链路：

1. Java 根据 role 解析 `space_id`
2. Java 在目标 `space_id` 下注册 source
3. Java 生成带 `space_id` 的 worker job
4. Python stub 读取 job 并返回 artifact/result
5. Java ingest result
6. Java 更新该 `space_id` 下的主状态

该链路的目标是验证：

- 边界正确
- contract 正确
- 主控权正确

它不是为了验证真实知识处理能力。

## 10. 验收

本轮完成标准：

- 所有共享 contract 已落地
- registry contract 与 registry example 已落地
- Java domain 与 application 骨架已落地
- Python worker stub 已落地
- 至少一条成功样例和一条失败样例可跑通
- role -> `space_id` -> source -> job -> result -> state 的追踪关系存在

## 11. 明确不做

本轮坚决不做：

- 真实文件扫描
- 真实快照切块
- 真实 graph artifacts
- 真实 wiki artifacts
- 真实 `graphify` 集成
- 真实增量更新逻辑
- 面向 `Codex` / `Trae` 的完整消费层

## 12. 风险与回退

主要风险：

- contract 过大，阻碍快速落地
- contract 过小，后续二期返工
- Java 先写出与 contract 不一致的内部模型
- Python stub 过早长成真实实现，污染边界

回退策略：

- 允许保留扩展字段
- 不允许先跳过 contract 直接写实现
- 不允许把真实业务逻辑偷偷塞进 stub

## 13. 结论

`Sprint 1` 的本质不是“开始做知识系统功能”，而是“先固定后面不会轻易推翻的边界”。

本轮实现应严格收敛为：

- contract
- registry
- `space_id`
- 状态
- Java 主状态骨架
- Python worker stub
- 最小假链路

只有这五件事完成，`Sprint 2` 才值得开始。
