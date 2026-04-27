# LiteFlow Application Orchestration POC Design

日期：2026-04-25

关联文档：

- [`2026-04-20-private-knowledge-base-design.md`](./2026-04-20-private-knowledge-base-design.md)
- [`2026-04-20-private-knowledge-base-implementation-plan.md`](./2026-04-20-private-knowledge-base-implementation-plan.md)
- [`2026-04-25-snapshot-scan-batch-coverage-design.md`](./2026-04-25-snapshot-scan-batch-coverage-design.md)

## 1. 背景

当前 `knowledge/service/core` 的应用层仍以手写 `*Process` 编排为主：

- `ResolveKnowledgeSpaceBindingProcess`
- `RegisterSourceProcess`
- `ResumeSnapshotScanProcess`
- `SubmitWorkerJobProcess`
- `IngestWorkerResultProcess`

这类实现能跑通当前 Sprint 2 骨架，但随着后续增加 `.rwa` 来源规则、多批扫描补全、worker 调度和结果回写，应用层会持续堆叠条件分支和中间状态，最终变成“流程散落在 process 和 controller 中”的结构。

本轮目标不是一次性重写所有应用流程，而是验证 LiteFlow 是否适合作为 application orchestration 底座。如果验证通过，再逐步把其他应用流程迁移到同一框架下。

## 2. 本轮设计目标

本轮 LiteFlow POC 只覆盖两条当前最关键、且依赖最稳定的同步链路：

- `registerSource`
- `resumeSnapshotScan`

本轮必须达到的效果：

- LiteFlow 直接承接 application orchestration
- `CoreController` 不再直接依赖 `RegisterSourceProcess`、`ResumeSnapshotScanProcess`
- chain/node 直接操作 repository、scanner 和 flow context
- 保持现有 domain、repository、contract、JSONL 存储格式不变
- 保持 demo 行为与当前实现一致

本轮明确不做：

- 不迁 `SubmitWorkerJobProcess`
- 不迁 `IngestWorkerResultProcess`
- 不引入 worker 重试、补偿、并行调度
- 不改变 `.rwa` 规则
- 不修改 `SourceSnapshotScanner` 的核心扫描语义

## 3. 方案选择

可选方案有三类：

### 3.1 方案 A：LiteFlow node 内部复用旧 process

优点：

- 改动最小
- 验证最快

缺点：

- LiteFlow 只是外层壳
- 保留 node -> process -> repository 的双层编排
- 后续仍需要第二次拆除旧 process

### 3.2 方案 B：LiteFlow 直接成为 orchestration 底座

优点：

- 从第一批开始就用真实目标架构
- 避免留下过渡壳
- 便于后续统一迁移其他流程

缺点：

- POC 改动比方案 A 大
- 需要先定义清楚上下文对象和节点边界

### 3.3 方案 C：先写一个脱离主链路的 LiteFlow demo

优点：

- 最容易跑通

缺点：

- 验证结果失真
- 后续仍需要再做一次真实迁移

设计结论：

- 采用方案 B
- 但范围只限 `registerSource + resumeSnapshotScan`
- 先验证 LiteFlow 是否能承接当前 Sprint 2 主链路
- 验证成功后，再逐步迁移其他流程

## 4. 架构边界

LiteFlow 在本项目中的职责，只是 application orchestration，不是 domain engine，也不是基础设施层。

分层边界保持如下：

- `domain/`
  继续承载稳定业务对象，如 `SourceRecord`、`SnapshotRecord`、`UnitRef`
- `repository/`
  继续承载 source/snapshot/state 的持久化接口
- `adapter/outbound/`
  继续承载 JSONL、demo 等 repository 实现
- `application/intake/`
  继续承载 `SourceIntakeConfig`、`SourceIntakeRequest`、`SourceSnapshotScanner`
- `application/flow/`
  新增 LiteFlow 编排层，负责 chain、node、flow context、flow facade

这意味着：

- LiteFlow 不进入 domain 层
- LiteFlow 不替代 repository
- LiteFlow 不负责文件扫描细节
- LiteFlow 只负责编排“谁先做、谁后做、失败时如何终止、结果如何回填”

补充约束：

- 当前 POC 不依赖 Spring 容器
- 因此节点注册必须在 LiteFlow 初始化前显式完成
- 带 repository/config 依赖的节点当前不能依赖反射直接构造
- POC 允许把运行时 repository/config 放进 flow context，作为最小可运行方案

## 5. 目录与对象设计

新增目录建议：

- `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/`
- `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/context/`
- `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/register/`
- `knowledge/service/core/src/main/java/com/meta_claw/knowledge/core/application/flow/resume/`

新增核心对象建议：

- `KnowledgeFlowFacade`
- `RegisterSourceFlowContext`
- `ResumeSnapshotScanFlowContext`

上下文对象统一要求：

- 使用 `@Data`
- 使用 `@SuperBuilder(toBuilder = true)`
- 使用 `@NoArgsConstructor`
- 使用 `@AllArgsConstructor`
- 入参和中间态尽量通过 context 传递，不在节点之间散落标量参数

LiteFlow chain 只依赖强类型 context，不使用匿名 `Map` 传值。

截至 2026-04-25 当前代码验证结果：

- LiteFlow POC 已在 `knowledge/service/core` 中落地
- `CoreController` 的 `registerSource`、`resumeSnapshotScan` 已改走 `KnowledgeFlowFacade`
- `registerSourceChain`、`resumeSnapshotScanChain` 已通过 `./gradlew run` 实际跑通
- 当前非 Spring 运行方式采用 `LiteFlowNodeBuilder.createCommonNode()` 在 `KnowledgeFlowExecutor` 中显式注册节点
- 运行时依赖当前通过 flow context 注入到节点中，而不是通过节点构造注入

## 6. Register Source 链路设计

### 6.1 目标

让 `registerSource` 这条主链路由 LiteFlow 承接，但行为与当前手写 `RegisterSourceProcess` 一致。

### 6.2 `RegisterSourceFlowContext`

建议至少包含：

- `SourceRegistrationRequest request`
- `AgentRoleBinding binding`
- `SourceRecord incomingSourceRecord`
- `SourceRecord resolvedSourceRecord`
- `SnapshotRecord candidateSnapshot`
- `SnapshotRecord latestSnapshot`
- `SourceRegistrationResult result`
- `Boolean unchanged`
- `SourceIntakeConfig sourceIntakeConfig`

说明：

- request 和 binding 负责把 role 解析为 `spaceId`
- resolved source 负责承载最终 `sourceId`、`latestSnapshotId` 等当前视图
- candidate snapshot 代表本次新生成的 snapshot 候选
- latest snapshot 代表仓库中已有最新快照
- result 为链路最终输出

### 6.3 节点划分

建议拆为：

- `ResolveKnowledgeSpaceNode`
  负责把 `roleName` 解析成 `AgentRoleBinding`
- `BuildSourceRecordNode`
  负责把 request 转成 domain source，并补齐稳定 `sourceId`
- `LoadLatestSnapshotNode`
  负责查询 source registry 与最新 snapshot
- `CreateSnapshotNode`
  负责调用 `SourceSnapshotScanner` 生成 candidate snapshot
- `DecideUnchangedNode`
  负责对比 fingerprint，判定是否 `unchanged`
- `PersistChangedSourceAndSnapshotNode`
  负责变化路径的 source/snapshot 双写
- `PersistUnchangedSourceNode`
  负责未变化路径只回写 source 当前视图
- `BuildSourceRegistrationResultNode`
  负责输出 `SourceRegistrationResult`

### 6.4 链路规则

`registerSourceChain` 的语义为：

1. resolve space
2. build source current view
3. load latest source/snapshot facts
4. create candidate snapshot
5. compare fingerprint
6. 根据 `unchanged` 分流持久化
7. build result

必须保持的语义：

- 首次注册生成新 snapshot
- 未变化时复用已有 snapshot，不新建 snapshot
- `SourceRecord.latestSnapshotId` 始终指向当前 source 最新可用 snapshot
- source current view 与 snapshot history 分工不改变

## 7. Resume Snapshot Scan 链路设计

### 7.1 目标

让 `resumeSnapshotScan` 由 LiteFlow 承接，但保持当前“每次只补一批”的行为。

### 7.2 `ResumeSnapshotScanFlowContext`

建议至少包含：

- `String sourceId`
- `SourceRecord sourceRecord`
- `SnapshotRecord latestSnapshot`
- `SnapshotRecord nextSnapshot`
- `SnapshotRecord resultSnapshot`
- `SourceIntakeConfig sourceIntakeConfig`
- `Boolean resumeNeeded`

### 7.3 节点划分

建议拆为：

- `LoadSourceForResumeNode`
  负责按 `sourceId` 读取 source
- `LoadLatestSnapshotForResumeNode`
  负责读取当前 latest snapshot
- `DecideResumeNeededNode`
  负责判断是否为 `partial + nextScanCursor != null`
- `CreateNextSnapshotNode`
  负责调用 `SourceSnapshotScanner` 生成下一批 snapshot
- `PersistResumedSnapshotNode`
  负责保存新 snapshot，并回写 source `latestSnapshotId`
- `BuildResumeResultNode`
  负责返回最终 snapshot

### 7.4 链路规则

`resumeSnapshotScanChain` 的语义为：

1. load source
2. load latest snapshot
3. 判断是否需要续批
4. 需要续批则生成下一批 snapshot
5. 保存下一批 snapshot，并回写 source 最新指针
6. 返回结果 snapshot

必须保持的语义：

- `partial + nextScanCursor != null` 才继续
- 每次调用最多生成一个新 snapshot
- snapshot 继续保持 immutable
- 没有可续批条件时直接返回 latest snapshot

## 8. Controller 与 Facade 调整

`CoreController` 后续不应继续直接注入以下对象：

- `RegisterSourceProcess`
- `ResumeSnapshotScanProcess`

替代方式：

- controller 只依赖 `KnowledgeFlowFacade`
- facade 提供：
  - `registerSource(SourceRegistrationRequest request)`
  - `resumeSnapshotScan(String sourceId)`

这能把 controller 从“直接知道多个 application process”收敛到“只知道统一 orchestration facade”。

## 9. 旧代码处置策略

POC 第一阶段不要求立即删除旧类：

- `RegisterSourceProcess`
- `ResumeSnapshotScanProcess`

但要求：

- 它们不再是 controller 主路径依赖
- LiteFlow chain 成为主执行路径
- 旧 process 仅作为对照、回退或短期迁移缓冲存在

待 POC 验证通过后，再决定是否彻底删除。

## 10. 错误处理原则

POC 阶段不引入 LiteFlow 高级恢复机制，只保留最小失败语义：

- source 不存在：
  抛 `IllegalArgumentException`
- latest snapshot 缺失：
  抛 `IllegalStateException`
- 节点执行异常：
  直接中止 chain

本轮不做：

- 自动重试
- 补偿事务
- 并行节点
- fallback chain

因为当前目标是先验证“LiteFlow 是否能稳定承接现有同步主链路”，而不是一次性设计完整工作流引擎能力。

## 11. 验证标准

POC 验证通过必须满足：

- `registerSource` 行为与当前 demo 一致
- `resumeSnapshotScan` 行为与当前 demo 一致
- `latestSnapshotId`、`scanStatus`、`nextScanCursor` 语义不回归
- `source-registry.jsonl` 与 `snapshot-store.jsonl` 的结果不出现明显行为漂移
- `CoreController` 不再直接依赖 `RegisterSourceProcess`、`ResumeSnapshotScanProcess`
- `./gradlew classes` 通过
- `./gradlew run` 通过

当前验收状态：

- 已满足
- `registerSourceChain` 和 `resumeSnapshotScanChain` 已在 demo 中实际执行
- 当前 demo store 非空，因此“首次注册”日志可能表现为 `unchanged=true`，这是受现有持久化数据影响，不表示链路失败

## 12. 后续迁移顺序

POC 验证通过后，再按以下顺序扩展：

1. 更新 spec 与 implementation plan，标记 LiteFlow 已成为 application orchestration 底座
2. 评估并删除不再需要的旧 process
3. 迁移 `SubmitWorkerJobProcess`
4. 迁移 `IngestWorkerResultProcess`
5. 再评估是否引入 LiteFlow 的重试、并行、编排监控能力

这个顺序的原则是：

- 先验证主链路
- 再统一文档
- 再扩展其他流程
- 最后再考虑高级特性
