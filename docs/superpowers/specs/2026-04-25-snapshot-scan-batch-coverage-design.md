# Snapshot Scan Batch Coverage Design

日期：2026-04-25

关联文档：

- [`2026-04-20-private-knowledge-base-design.md`](./2026-04-20-private-knowledge-base-design.md)
- [`2026-04-20-private-knowledge-base-implementation-plan.md`](./2026-04-20-private-knowledge-base-implementation-plan.md)
- [`2026-04-24-source-registry-snapshot-boundary-design.md`](./2026-04-24-source-registry-snapshot-boundary-design.md)

## 1. 背景

当前 `SourceSnapshotScanner` 已能为目录生成根 `UnitRef` 和最多一个配置批次大小的文件 `UnitRef`。这个上限只能作为骨架阶段的安全阈值，不能被解释为“代码库分析最多只看固定数量的文件”。

如果只保留截断结果而不记录覆盖范围，下游 graph/wiki/answer 很容易把局部快照误判为完整代码库事实，违反“禁止局部冒充整体”和“禁止无覆盖声明的结论”的防错原则。

## 2. 设计结论

后续实现应将单批扫描大小配置化，而不是固定写死在方法内部。

核心规则：

- snapshot 可以由多个 scan batch 逐步补全
- 单个 batch 允许有 `unitLimit`
- snapshot 需要明确 `scanStatus`
- 下游只能在 `scanStatus=complete` 时输出全局结论
- `partial` 或 `truncated` 只能产生局部知识、候选知识或 `needs_review`

## 3. 状态模型

### 3.1 `scanStatus`

`SnapshotRecord.scanStatus` 表示当前快照的扫描覆盖状态：

- `complete`
  当前 snapshot 已覆盖该来源在本轮策略下需要纳入的全部单位。
- `partial`
  当前 snapshot 已纳入至少一个 batch，但还有后续 batch 未完成。
- `truncated`
  当前 snapshot 因安全上限、错误或策略限制停止，不能自动继续补全。
- `failed`
  扫描失败，只能保留最小 source/snapshot 事实。

### 3.2 覆盖计数字段

`SnapshotRecord` 后续应增加：

- `unitLimit`
  单批最多纳入多少个子单元。
- `includedUnitCount`
  当前 snapshot 已写入的 `UnitRef` 数量，包括 root unit。
- `scanBatchCount`
  已完成的扫描批次数。
- `nextScanCursor`
  下一批扫描的稳定游标；没有后续批次时为 `null`。

暂不增加 `totalUnitCount`，因为准确总数可能需要完整遍历超大来源，会违背先分批、可恢复扫描的目标。后续可通过异步索引或 manifest 阶段补充。

## 4. 数据流

1. `SourceSnapshotScanner` 创建 root unit。
2. scanner 按稳定排序读取最多 `unitLimit` 个文件或内容块。
3. 本批生成 file/chunk `UnitRef`。
4. 若本批已经覆盖扫描策略下的全部候选，`scanStatus=complete`。
5. 若还有后续批次，`scanStatus=partial`。
6. 若因为硬限制或错误无法继续，`scanStatus=truncated` 或 `failed`。
7. `RegisterSourceProcess` 仍只负责 source/snapshot 协同写入，不承载扫描细节。

## 5. 下游约束

- `scanStatus=complete`
  允许进入完整 graph/wiki 生成。
- `scanStatus=partial`
  只能生成局部 artifact，且必须带 `coverage` 和 `scope`。
- `scanStatus=truncated`
  只能进入候选知识或 review 队列，不能发布成稳定全局结论。
- `scanStatus=failed`
  只能记录失败事实和可重试问题。

## 6. 下一步实现切片

第一步已完成元数据对齐，暂不实现多批扫描调度：

- `SnapshotRecord` 已增加 `scanStatus`、`unitLimit`、`includedUnitCount`、`scanBatchCount`
- `snapshot-store.schema.json` 和 `snapshot-store.example.json` 已增加对应字段
- `SourceSnapshotScanner` 已根据本次 unit 生成结果填充这些字段
- 当前单批实现如果没有超过上限则标记 `complete`
- 当前单批会多探测 1 个文件；如果存在下一批则标记 `partial`，为后续 batch 调度留出口

第二步已实现最小 batch cursor，resume 编排仍是后续工作：

- `SnapshotRecord` 已增加 `nextScanCursor`
- `snapshot-store.schema.json` 和 example 已增加 `next_scan_cursor`
- `SourceSnapshotScanner.createSnapshot(sourceRecord, scanCursor)` 已支持从游标后继续生成下一批
- `RegisterSourceProcess` 当前仍只创建第一批 snapshot
- `ResumeSnapshotScanProcess` 已支持每次调用从 `nextScanCursor` 继续生成一批新 snapshot
- 当前策略采用同一 source 下的连续 batch snapshots，保持已有 snapshot immutable
- 后续仍需增加 loop-to-complete 调度和真实持久化

## 7. 验收标准

- snapshot contract 能表达扫描覆盖状态
- example 明确展示完整扫描样例
- Java demo 能输出 `scanStatus`
- 下游文档明确禁止将 `partial/truncated` 当作完整代码库分析
- 当前批次大小由 `SourceIntakeConfig` 控制，而不是永久分析上限
