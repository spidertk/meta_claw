# LiteFlow Runtime Dependencies And Worker Flow Design

日期：2026-04-25

关联文档：

- [`2026-04-25-liteflow-application-orchestration-poc-design.md`](./2026-04-25-liteflow-application-orchestration-poc-design.md)
- [`2026-04-20-private-knowledge-base-implementation-plan.md`](./2026-04-20-private-knowledge-base-implementation-plan.md)

## 1. 背景

`registerSource` 和 `resumeSnapshotScan` 两条 LiteFlow 链已经在 `knowledge/service/core` 中跑通，说明 LiteFlow 可以承接当前 application orchestration 主链路。

但当前 POC 还存在两个明显工程问题：

- flow context 已开始直接持有多个 repository 和 config，逐步演变成散装的运行时容器
- `submitWorkerJob` 和 `ingestWorkerResult` 仍停留在旧的 `*Process` 路径，没有进入统一的 LiteFlow 编排层

如果继续按当前方式直接扩展，会把临时方案复制到更多链路里，后续整理成本会明显上升。

## 2. 本轮目标

本轮只做两类事情：

1. 收敛 LiteFlow 运行时依赖注入方式
2. 把剩余两条 application 流程迁到 LiteFlow：
   - `submitWorkerJob`
   - `ingestWorkerResult`

本轮必须达到的效果：

- flow context 不再平铺持有多个 repository/config 字段
- 统一引入 `FlowRuntimeDependencies` 作为 LiteFlow 运行时依赖载体
- `KnowledgeFlowFacade` 成为四条主链路的统一入口：
  - `registerSource`
  - `resumeSnapshotScan`
  - `submitWorkerJob`
  - `ingestWorkerResult`
- `CoreController` 不再直接依赖：
  - `ResolveKnowledgeSpaceBindingProcess`
  - `SubmitWorkerJobProcess`
  - `IngestWorkerResultProcess`

本轮明确不做：

- 不升级 Spring Boot
- 不改 domain / repository contract
- 不改 worker transport contract
- 不引入 LiteFlow 重试、补偿、并行编排

## 3. 设计结论

### 3.1 收敛方式

当前 POC 中 repository/config 直接挂在 `RegisterSourceFlowContext`、`ResumeSnapshotScanFlowContext` 上，只适合作为最小可运行方案，不适合作为后续统一模式。

本轮采用的收敛方式：

- 新增 `FlowRuntimeDependencies`
- 由 `KnowledgeFlowFacade` 统一构造并注入
- 所有 flow context 只保留：
  - 业务输入
  - 业务中间态
  - 业务输出
  - 一个 `runtimeDependencies`

这样可以把“业务上下文”和“运行时基础设施依赖”分离开。

### 3.2 为什么不继续把 repository 平铺在 context 上

继续平铺会带来三个问题：

- 每新增一条链都要复制同一批 repository/config 字段
- context 语义会越来越像 service locator
- 后续切 Spring Boot 或更换装配方式时，修改面会成倍扩大

因此，当前阶段最合理的工程止损点，就是把依赖收口成一个对象。

## 4. `FlowRuntimeDependencies` 设计

新增对象建议：

- `FlowRuntimeDependencies`

职责：

- 统一承载 LiteFlow 节点执行时需要访问的基础设施对象
- 由 facade 注入
- 由节点只读使用

建议字段：

- `KnowledgeSpaceBindingRepository knowledgeSpaceBindingRepository`
- `SourceRegistryRepository sourceRegistryRepository`
- `SnapshotStoreRepository snapshotStoreRepository`
- `KnowledgeStateRepository knowledgeStateRepository`
- `SourceIntakeConfig sourceIntakeConfig`

统一要求：

- 使用 `@Data`
- 使用 `@SuperBuilder(toBuilder = true)`
- 使用 `@NoArgsConstructor`
- 使用 `@AllArgsConstructor`

当前不放入 `SubmitWorkerJobProcess` 或 `IngestWorkerResultProcess` 这类旧 process，因为目标是让节点直接操作 repository/domain，而不是继续包旧流程。

## 5. Flow Context 收敛规则

本轮后所有 LiteFlow context 统一遵循以下规则：

- context 只保留本链路的业务输入/中间态/输出
- 基础设施依赖统一挂在 `runtimeDependencies`
- 节点通过 `context.getRuntimeDependencies()` 取 repository/config
- 不允许在新链路里重新平铺 repository/config 字段

这条规则适用于：

- `RegisterSourceFlowContext`
- `ResumeSnapshotScanFlowContext`
- 本轮新增的 worker 相关 flow context

## 6. Submit Worker Job 链设计

### 6.1 目标

把当前 `submitWorkerJob` 从 `SubmitWorkerJobProcess` 主路径迁到 LiteFlow，并保持现有行为不变。

### 6.2 `SubmitWorkerJobFlowContext`

建议至少包含：

- `SubmitWorkerJobRequest request`
- `AgentRoleBinding binding`
- `WorkerJob workerJob`
- `WorkerJob result`
- `FlowRuntimeDependencies runtimeDependencies`

### 6.3 节点划分

建议拆为：

- `ResolveKnowledgeSpaceNode`
  继续复用统一 role -> space 解析节点
- `BuildWorkerJobNode`
  把 request 转为带 `spaceId` 的 `WorkerJob`
- `SubmitWorkerJobNode`
  负责提交或回传当前 `WorkerJob`
- `BuildSubmitWorkerJobResultNode`
  负责把结果写回 context

### 6.4 链路语义

`submitWorkerJobChain`：

1. resolve space
2. build worker job
3. submit worker job
4. build result

当前语义必须保持：

- `spaceId` 仍由 role 解析得到
- 当前如果 submit 只是最小 stub，也保持 stub 语义，不扩大外部执行范围

## 7. Ingest Worker Result 链设计

### 7.1 目标

把当前 `ingestWorkerResult` 从 `IngestWorkerResultProcess` 主路径迁到 LiteFlow。

### 7.2 `IngestWorkerResultFlowContext`

建议至少包含：

- `WorkerResultEnvelope envelope`
- `WorkerResult workerResult`
- `WorkerResult result`
- `FlowRuntimeDependencies runtimeDependencies`

### 7.3 节点划分

建议拆为：

- `ReadWorkerResultEnvelopeNode`
  负责把 transport envelope 转为 domain `WorkerResult`
- `IngestKnowledgeStateNode`
  负责把 artifacts / issues 写入 `KnowledgeStateRepository`
- `BuildIngestWorkerResultNode`
  负责返回最终结果

### 7.4 链路语义

`ingestWorkerResultChain`：

1. read envelope
2. ingest knowledge state
3. build result

必须保持的语义：

- 当前 `IngestWorkerResultProcess` 的最小 state write 行为不回归
- 不新增额外 promotion、review、retry 逻辑

## 8. Controller 与 Facade 收敛

本轮之后，`CoreController` 应进一步收敛为只依赖：

- `KnowledgeFlowFacade`

`submitWorkerJob` 和 `ingestWorkerResult` 也应改走 facade，而不是继续直接依赖旧 process。

`KnowledgeFlowFacade` 本轮建议提供四个稳定入口：

- `registerSource(...)`
- `resumeSnapshotScan(...)`
- `submitWorkerJob(...)`
- `ingestWorkerResult(...)`

这一步完成后，controller 的职责会收敛为：

- 接收请求
- 组装最小输入 context
- 调 facade

## 9. 旧代码处置策略

本轮仍不强制立即删除旧类：

- `ResolveKnowledgeSpaceBindingProcess`
- `SubmitWorkerJobProcess`
- `IngestWorkerResultProcess`

但要求：

- 它们不再是 controller 主路径依赖
- 四条主链路统一走 LiteFlow facade
- 旧 process 只作为短期对照或回退缓冲存在

## 10. 错误处理原则

本轮仍保持 POC 阶段的最小错误处理：

- 节点异常直接中止 chain
- 不引入补偿
- 不引入自动重试
- 不引入并行分支

worker 两条链当前只做“迁入统一编排底座”，不做“增强执行模型”。

## 11. 验证标准

本轮完成后必须满足：

- `FlowRuntimeDependencies` 已替代 context 平铺 repository/config
- `registerSource` / `resumeSnapshotScan` 已完成依赖收敛且行为不回归
- `submitWorkerJob` 已迁入 LiteFlow
- `ingestWorkerResult` 已迁入 LiteFlow
- `CoreController` 只依赖 `KnowledgeFlowFacade`
- `./gradlew classes` 通过
- `./gradlew run` 通过

当前验收状态：

- 已满足
- `submitWorkerJobChain` 和 `ingestWorkerResultChain` 已在 demo 中实际执行
- `CoreController` 当前已只依赖 `KnowledgeFlowFacade`
- demo 中 `WorkerResultEnvelope.spaceId` 已与 `submitWorkerJob` 产出的 `spaceId` 对齐，避免出现 submit/ingest 示例空间不一致

当前已知工程备注：

- `ResolveWorkerKnowledgeSpaceNode` 目前与 `ResolveKnowledgeSpaceNode` 职责接近，但由于两条链的 context 类型不同，当前保留为独立节点
- 这是当前分类型 context 设计下的可接受重复，不是功能错误；后续如果要继续压缩重复，应先统一更高层的 role-resolve 输入模型

## 12. 后续顺序

本轮完成后，再按以下顺序推进：

1. 更新总实现计划中的当前实现基线
2. 评估是否删除已脱主路径的旧 process
3. 再决定是否推进 Spring Boot 化
4. 再决定是否引入 LiteFlow 的更高级特性

这里的原则是：

- 先把结构收干净
- 再迁剩余链路
- 再考虑框架升级
