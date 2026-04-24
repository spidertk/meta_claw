# Source Registry / Snapshot Boundary Design

日期：2026-04-24
关联文档：

- [`2026-04-20-private-knowledge-base-design.md`](./2026-04-20-private-knowledge-base-design.md)
- [`2026-04-20-private-knowledge-base-implementation-plan.md`](./2026-04-20-private-knowledge-base-implementation-plan.md)
- [`2026-04-21-private-knowledge-base-sprint1-design.md`](./2026-04-21-private-knowledge-base-sprint1-design.md)

## 1. 目的

本文只解决一个具体问题：明确 `source_registry` 与 `snapshot_store` 的职责边界，消除当前实现和命名给人带来的混乱。

本轮设计目标：

- 明确 `SourceRegistryRepository` 与 `SnapshotStoreRepository` 不是重复仓库
- 固定 `1 source -> N snapshots` 的关系模型
- 保留 `SourceRecord.latestSnapshotId` 作为当前最新快照的派生指针
- 明确注册流程中的双写一致性规则
- 让文档、代码字段、接口命名保持一致

本轮不做：

- 引入第三个 head/projection repository
- 合并 source 与 snapshot 为单一 repository
- 设计真实数据库事务方案
- 扩展到 graph/wiki/knowledge_state 之外的其他边界

## 2. 核心结论

### 2.1 模型关系

本轮固定以下关系：

`1 source -> N snapshots`

含义如下：

- `source`
  表示一个被系统长期管理的来源身份
- `snapshot`
  表示该来源在某次采集时刻的内容状态

因此：

- `SourceRecord` 是“来源稳定身份 + 当前视图”
- `SnapshotRecord` 是“来源历史快照”

二者不是父子仓库，也不是重复仓库，而是同一条 intake 主链路里的两种不同状态模型。

### 2.2 Repository 职责

`SourceRegistryRepository` 负责：

- 保存来源稳定标识
- 保存来源元数据
- 保存来源当前处理状态
- 保存 `latestSnapshotId` 派生指针

`SnapshotStoreRepository` 负责：

- 保存按 `sourceId` 关联的不可变快照历史
- 提供按 `snapshotId` 查询
- 提供按 `sourceId` 查询最新快照的能力，供流程判断变化使用

边界约束：

- `SourceRegistryRepository` 不保存完整快照历史
- `SnapshotStoreRepository` 不拥有来源当前状态定义权
- “当前最新快照是谁”属于 source 当前视图的一部分，因此由 `SourceRecord.latestSnapshotId` 显式表达

## 3. 字段设计

### 3.1 `SourceRecord`

`SourceRecord` 表示来源稳定身份与当前视图，保留以下字段：

- `spaceId`
- `sourceId`
- `sourceType`
- `location`
- `displayName`
- `status`
- `description`
- `workspaceIdentity`
- `snapshotHint`
- `createdAt`
- `updatedAt`
- `latestSnapshotId`

字段语义约束：

- `sourceId`
  是来源稳定标识；同一来源多次采集时保持不变
- `status`
  表示当前来源处理状态，不表示历史版本状态
- `latestSnapshotId`
  表示当前 source 指向的最新快照；它是派生指针，不是历史本体

### 3.2 `SnapshotRecord`

`SnapshotRecord` 表示来源在某次采集时刻的不可变内容快照，保留以下字段：

- `spaceId`
- `snapshotId`
- `sourceId`
- `contentFingerprint`
- `capturedAt`
- `units`

字段语义约束：

- `snapshotId`
  是本次快照的唯一标识
- `sourceId`
  表示该快照归属于哪个来源
- `contentFingerprint`
  用于判定内容是否变化
- `capturedAt`
  表示本次快照的采集时间
- `units`
  表示该快照下可追踪的内容单元引用

## 4. 一致性规则

### 4.1 `latestSnapshotId` 规则

- `latestSnapshotId == null`
  只允许出现在 source 已创建但尚未形成成功快照时
- 新内容生成新 snapshot 后
  `SourceRecord.latestSnapshotId` 必须回写为新 `snapshotId`
- 内容未变化时
  `SourceRecord.latestSnapshotId` 必须继续指向已有最新快照

### 4.2 Snapshot 不可变规则

- `SnapshotRecord` 一旦写入，即视为历史事实，不允许被覆盖为另一份内容
- 新内容只能通过新增 snapshot 表达，不能修改旧 snapshot
- source 当前状态允许变化，但不能反向改写 snapshot 历史

### 4.3 注册流程规则

`RegisterSourceProcess` 按以下顺序协调两个 repository：

1. 解析或生成稳定 `sourceId`
2. 读取已有 `SourceRecord`
3. 基于当前来源信息构造新的 source 当前视图
4. 生成候选 `SnapshotRecord`
5. 读取该 `sourceId` 的最新 snapshot
6. 比较 `contentFingerprint`
7. 若未变化：
   - 不新增 snapshot
   - 复用已有最新 snapshot
   - 保持 `latestSnapshotId`
   - 更新 source 当前状态为 `unchanged`
8. 若已变化：
   - 写入新 snapshot
   - 回写 `latestSnapshotId`
   - 更新 source 当前状态

### 4.4 双写语义

本轮接受 source/snapshot 的协同双写，但必须在文档和代码中显式承认这一点：

- `SnapshotStoreRepository` 写入的是历史事实
- `SourceRegistryRepository` 写入的是当前视图
- 二者由 `RegisterSourceProcess` 协调，不由任一 repository 吞并另一方职责

当前阶段不额外引入事务抽象，但接口注释必须明确“双写由应用层保持一致性”。

## 5. 命名与注释规范

### 5.1 命名规范

本轮统一采用以下术语：

- `source registry`
  来源稳定身份与当前视图注册表
- `snapshot store`
  来源快照历史存储
- `latestSnapshotId`
  source 指向当前最新快照的派生指针

禁止在文档或代码中混用以下模糊表达：

- `head snapshot`
- `current snapshot state`
- `source snapshot table`

除非它们被明确映射到上述正式术语。

### 5.2 中文注释规范

本轮要求以下代码元素补齐中文注释：

- `SourceRecord` 与 `SnapshotRecord` 的所有字段
- 相关嵌套对象字段
- `SourceRegistryRepository` 与 `SnapshotStoreRepository` 的类注释和方法注释
- sample repository 的类注释和方法注释
- `RegisterSourceProcess` 的类注释和核心方法注释
- demo 入口的装配说明注释

注释要求：

- 解释“表示什么，不表示什么”
- 解释“职责边界与依赖关系”
- 解释“何时更新 source，何时写 snapshot”
- 保持简短，不写空话

增量变更要求：

- 本轮涉及的所有新增代码和修改代码都必须补齐与职责匹配的中文注释
- 不允许只给新文件加注释而保留同一轮修改中的旧代码无注释
- 如果某段代码在本轮被改动，就需要顺手补足该段上下文中缺失的必要注释

## 6. 实现调整范围

本轮实现只覆盖以下改动：

- 在 `SourceRecord` 中新增 `latestSnapshotId`
- 在 source 注册链路中维护 `latestSnapshotId`
- 为核心 domain、repository、application、sample adapter 补齐中文注释
- 修正示例装配代码，确保 demo 链路能体现 `latestSnapshotId` 语义
- 同步修正已有设计文档和实现文档中的 source/snapshot 定义

本轮不做：

- 真实持久化层重构
- 事务管理器
- 新建 projection/head repository
- 跨模块大规模重命名

## 7. 文档同步要求

以下文档在实现时必须同步检查并修正：

- `2026-04-20-private-knowledge-base-design.md`
- `2026-04-20-private-knowledge-base-implementation-plan.md`
- `2026-04-21-private-knowledge-base-sprint1-design.md`

同步目标：

- `source_registry`
  明确定义为“来源稳定身份 + 当前状态 + latest_snapshot_id”
- `snapshot_store`
  明确定义为“按 source 关联的不可变快照历史”
- 注册主链路
  明确为 `source_registry -> snapshot_store` 的协同更新，而不是二选一主表

如果文档中出现以下问题，必须一起修正：

- 将 snapshot 描述成 source 当前状态主表
- 没有说明 `latestSnapshotId` 或等价概念
- 字段名、术语名与代码实现不一致

## 8. 验收标准

完成后应满足：

- 阅读 `SourceRecord` 与 `SnapshotRecord` 就能直接理解 identity / history 分工
- 阅读两个 repository 接口就能明确 `1 source -> N snapshots`
- 阅读 `RegisterSourceProcess` 就能看懂未变化与已变化两条分支的处理逻辑
- 代码注释、设计文档、实现文档使用同一套术语
- demo 链路能验证：
  - 首次注册生成 snapshot，并回写 `latestSnapshotId`
  - 重复注册且内容未变时不生成新 snapshot，`latestSnapshotId` 不变

## 9. 风险与取舍

本设计接受一个明确取舍：为换取更清楚的职责边界与更快的当前状态读取，source 与 snapshot 之间存在应用层双写。

当前阶段这是可接受的，因为：

- 代码仍处于骨架阶段
- 读当前 source 状态的语义清晰度比消除所有双写更重要
- 后续若进入真实持久化，可再补事务或事件化方案，而无需推翻当前边界

本轮关键不是追求最终存储实现，而是先把模型边界讲清楚并固定下来。
