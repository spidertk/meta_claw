# LiteFlow Main Path Cleanup And Regression Design

日期：2026-04-27

## 1. 背景

`knowledge/service/core` 已经完成基于 LiteFlow 的 application orchestration 重构。当前 `CoreController` 已通过 `KnowledgeFlowFacade` 进入四条主链路：

- `registerSource`
- `resumeSnapshotScan`
- `submitWorkerJob`
- `ingestWorkerResult`

源码中仍保留旧的手写 application process 类。它们不再是 controller 主路径，也没有被当前 Java 源码引用。继续保留这些类会制造双主路径误解，让后续维护者不确定应修改 LiteFlow node 还是旧 process。

## 2. 目标

本轮目标是激进收敛当前可运行架构：

- 以 `CoreController -> KnowledgeFlowFacade -> LiteFlow chain/node` 作为唯一 application 主路径。
- 删除已脱离主路径且无源码引用的旧 application process。
- 不恢复已废弃的 `SourceIntakeSupport`。
- 更新当前 README 中过时的现状描述。
- 做一次覆盖四条 LiteFlow 主链路的整体回归。

## 3. 非目标

本轮不做以下事情：

- 不重写历史 specs/plans。历史文档保留迁移脉络，不作为当前运行架构说明。
- 不清理 `domain/`、`repository/`、`contracts/`、JSONL file adapter、demo/sample repository 等稳定边界。
- 不新增 JUnit 或测试框架。
- 不引入 Spring 容器。
- 不改变 LiteFlow chain 语义、domain contract 或 JSONL 存储格式。

## 4. 清理边界

删除候选仅限当前 Java 源码无外部引用的旧 application process：

- `RegisterSourceProcess`
- `ResumeSnapshotScanProcess`
- `SubmitWorkerJobProcess`
- `IngestWorkerResultProcess`
- `ResolveKnowledgeSpaceBindingProcess`

这些类的职责已经由 LiteFlow facade、context、node 和 chain 承接。删除后不应保留旧 process 作为 fallback，否则会重新引入双主路径。

如果删除后发现编译或端到端回归失败，优先修复 LiteFlow 主路径。只有当某个旧 process 内存在尚未迁移的真实行为时，才停止删除对应文件，并先把差异迁入 LiteFlow 节点或 facade。

## 5. 当前说明更新

`knowledge/service/core/README.md` 应描述当前模块的真实状态：

- role-to-space resolution 仍由 repository/facade 主路径支持。
- domain records 仍与 `knowledge/contracts` 对齐。
- repository interfaces 和 demo/file repository implementations 仍保留。
- application orchestration 已由 LiteFlow facade + chain/node 承接。
- internal worker transport models 仍与 external API request 分离。

README 不应继续暗示旧 use case skeleton 是当前主路径。

## 6. 回归设计

回归按三层执行。

第一层：`./gradlew classes`

验证删除旧 process 后 Java 编译、Lombok annotation processing、LiteFlow、Jackson 和 SLF4J 依赖仍完整。

第二层：`./gradlew run`

使用当前 `CoreApplication` 做端到端 demo 回归。该入口应覆盖：

- source registration
- snapshot resume when needed
- unchanged or repeated registration path
- worker job submission
- worker result ingestion

第三层：输出检查

检查 run 日志或异常。回归通过的最低标准是四条 facade 入口均成功返回，并且输出中包含 source、snapshot、worker job、worker result 的关键字段。

`knowledge/demo-store/*.jsonl` 可能已有历史数据，因此不强制要求每次 run 都表现为全新首次注册。只要链路成功且结果字段完整，即视为回归通过。不为制造干净状态而删除 demo store。

## 7. 风险与处理

主要风险是旧 process 中残留了 LiteFlow 节点未覆盖的行为。处理原则是：

- 编译失败说明仍有真实引用，先定位引用并判断是否应迁到 facade/node。
- 运行失败说明 LiteFlow 主路径存在缺口，先修主路径，不恢复旧 process。
- README 只更新当前状态，不改写历史设计记录。

本轮完成后，代码阅读路径应更明确：外部入口看 controller，应用编排看 facade、flow context、chain XML 和 node。
