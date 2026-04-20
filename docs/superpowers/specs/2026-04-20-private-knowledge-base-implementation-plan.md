# 私有知识中台执行计划

日期：2026-04-20
关联设计文档：
[`2026-04-20-private-knowledge-base-design.md`](./2026-04-20-private-knowledge-base-design.md)

## 1. 计划定位

本计划覆盖：

- `Phase 1` 最小闭环落地
- `Phase 2` 增量维护与治理增强

执行重心放在 `Phase 1`。`Phase 2` 保持里程碑级约束，不在本轮细拆到同等粒度。

核心原则：

- 先立 `Java State Core`
- 再接 `Python Worker Mesh`
- 先跑通闭环
- 再补强增量维护与知识治理

## 2. 总计划

### 2.1 Phase 1 目标

建立最小可运行闭环：

`.rwa source -> source_registry -> snapshot_store -> Python worker -> graph artifacts -> wiki artifacts -> Java-managed knowledge_state -> agent-facing minimal views`

完成后必须满足：

- 能登记本地文件、目录、仓库、受控外链副本
- 能生成稳定 `source_id`、`snapshot`、`unit_ref`
- 能由 Python 产出 graph/wiki artifacts
- 能由 Java 接收 artifacts 并落成主状态
- 能生成最小可用 wiki
- 能生成最小 agent-facing views
- 能区分 `new_source`、`unchanged`、`changed`

### 2.2 Phase 2 目标

在不推翻 `Phase 1` 边界的前提下，补强持续维护能力：

- 四层 diff 正式落盘
- `repo -> commit -> file/symbol` 增量链路
- `document -> snapshot -> section/chunk` 增量链路
- `knowledge_control_state` 的 `needs_review / stale / superseded` 流转
- `wiki_planner` 任务化
- agent-facing views 稳定化
- lint / audit / retry / partial rebuild

完成后必须满足：

- 局部变化只触发局部更新
- graph/wiki 更新有明确依据
- 高层知识结论有 promotion / review 规则
- Java 能稳定调度 Python worker 并接收结果

### 2.3 依赖顺序

必须先做：

1. schema 与状态边界
2. Java / Python job-artifact contract
3. source / snapshot 主链路
4. graph/wiki artifact 归档
5. agent-facing minimal views

可以后做：

- 精细 diff
- review gate 自动化
- 更复杂的 `wiki_planner`
- 更强的 `topic_routing`
- 私有模型扩展接口细化

### 2.4 语言分工

Java 负责：

- `source_registry`
- `snapshot_store`
- `knowledge_state`
- `knowledge_control_state`
- orchestration
- retrieval / planning
- agent-facing views

Python 负责：

- ingest normalize
- content extraction
- `graphify` integration
- diff worker
- wiki candidate generation
- lint / audit worker

### 2.5 里程碑

#### M1. 状态与契约冻结

完成标志：

- 核心 schema 定稿
- job / artifact contract 定稿
- 状态流转图定稿

#### M2. 来源与快照闭环

完成标志：

- `.rwa` 来源进入系统
- snapshot 正常落盘
- `unchanged / changed` 判定可用

#### M3. graph/wiki 最小闭环

完成标志：

- source/snapshot 可产出 graph artifacts
- source/snapshot 可产出 wiki artifacts
- artifact 与主状态可追溯

#### M4. agent-facing 最小可用

完成标志：

- Java 完成最小 orchestration
- `Codex` / `Trae` 可消费稳定 views

## 3. Phase 1 Sprint 计划

### 3.1 Sprint 1：状态骨架和契约骨架

目标：

- 固定不会轻易推翻的状态模型和边界

任务：

- 定义 `source_registry` schema
- 定义 `snapshot_store` schema
- 定义 `knowledge_assets` schema
- 定义 `knowledge_control_state` schema
- 定义 `unit_ref` 结构
- 定义最小状态流转：
  - `new_source`
  - `unchanged`
  - `partial_update`
  - `major_update`
  - `failed`
- 定义知识生命周期：
  - `candidate`
  - `active`
  - `needs_review`
  - `stale`
  - `superseded`
  - `rejected`
- 定义 Java -> Python `job contract`
- 定义 Python -> Java `artifact/result contract`

产出物：

- schema 文档
- 状态流转图
- job/artifact contract 文档
- 字段命名规范

完成标准：

- 任一来源可用统一 schema 表达
- 任一 worker 结果可用统一 artifact 表达
- Java 主状态和 Python 处理结果边界清晰
- 不依赖 `graphify` 私有细节也能理解主状态

测试与验收：

- schema 样例覆盖文件、仓库、文档三类来源
- job/result 样例覆盖成功、失败、部分成功三类结果

风险：

- schema 过大，影响一期落地
- schema 过小，迫使二期重写

回退策略：

- 允许保留扩展字段区
- 不允许临时退化成“随便写 JSON”

### 3.2 Sprint 2：source intake + snapshot 主链路

目标：

- 跑通来源进入系统并形成稳定快照

任务：

- 定义 `.rwa` 来源目录规则
- 定义来源登记命令接口
- 支持本地文件、目录、仓库、受控外链副本录入
- 实现 `source_id` 生成规则
- 实现 `content_fingerprint` 生成规则
- 实现 `snapshot_store` 写入
- 实现 `unit_ref` 父子/邻接关系落盘
- 实现最小 diff 判定：
  - `new_source`
  - `unchanged`
  - `changed`

产出物：

- `.rwa` 目录与元数据规范
- source intake 接口定义
- snapshot 样例
- 样本来源：
  - 一个本地仓库
  - 一个 PDF 或长文档

完成标准：

- 同一来源重复录入不会生成新身份
- 内容不变返回 `unchanged`
- 内容变化生成新 snapshot
- 切块后的 `unit_ref` 能回溯来源与父级结构
- 失败不会覆盖上一次成功快照

测试与验收：

- 文件/仓库/文档至少各一条正向路径
- 至少一条失败路径验证 `snapshot_immutability`

风险：

- `source_id` 规则不稳定
- 仓库与文档切块语义不统一

回退策略：

- 切块策略可阶段性不同
- `unit_ref` 格式必须统一

### 3.3 Sprint 3：graph foundation + wiki maintenance 最小闭环

目标：

- 让 source/snapshot 真正产出知识资产

任务：

- 定义 `graphify` 输出归档规范
- 接入 Python worker 调用 `graphify`
- 产出 graph artifacts
- 定义 graph 与 source/snapshot 的关联主键
- 定义 wiki 页面类型和最小模板
- 定义 `wiki_planner` 输出格式
- 定义 wiki 页面依赖映射
- 实现 summary / topic / source summary 最小生成链路
- 定义最小 promotion 规则：
  - graph signal -> `candidate`
  - stable knowledge -> `active`
  - coverage 不足 -> `needs_review`

产出物：

- graph artifact 样例
- wiki artifact 样例
- 页面依赖映射样例
- promotion 最小规则说明

完成标准：

- 一个仓库来源能产出 graph + wiki
- 一个文档来源能产出 graph + wiki
- graph/wiki 都能追溯到 source/snapshot
- 高层结论不会由单块摘要直接升级

测试与验收：

- 仓库样本和文档样本各完成一次闭环
- 至少一条 coverage 不足进入 `needs_review`

风险：

- `graphify` 产物与主状态绑定过死
- wiki 过早追求复杂结构

回退策略：

- wiki 先最小可审校
- 模板先稳定，后细化

### 3.4 Sprint 4：Java orchestration 接入 + agent-facing 最小视图

目标：

- 让系统具备稳定消费能力

任务：

- Java 接入 job dispatch / result ingestion
- 落地 `knowledge_state` 与 `knowledge_control_state`
- 定义 `topic summary view`
- 定义 `source-backed answer context`
- 定义 `review-needed view`
- 定义 coverage / scope 标记
- 定义最小 retrieval / planning 接口
- 把 `Codex` / `Trae` 消费约束落为稳定 views

产出物：

- Java orchestration 流程定义
- agent-facing views schema
- retrieval / planning 最小接口
- 端到端样例：
  - source -> snapshot -> graph/wiki -> state -> view

完成标准：

- Java 能调度 Python worker
- Java 能接收并落盘 worker 结果
- `Codex` / `Trae` 可消费稳定 views
- agent 不需要理解 graph/wiki 私有结构

测试与验收：

- 至少一条完整端到端链路
- 至少一条 worker 失败后仍可保留旧状态

风险：

- 太早追求复杂检索
- retrieval 接口过度贴合单一 agent

回退策略：

- 先只做最小 view
- 先保证 `Codex` / `Trae` 共用基础视图

## 4. Phase 2 工作清单

`Phase 2` 不按本轮继续细拆 sprint，只保留执行清单：

- 正式落地四层 diff
- 仓库增量：`repo -> commit -> file/symbol`
- 文档增量：`document -> snapshot -> section/chunk`
- `knowledge-level impact` 正式落盘
- `wiki_planner` 任务化
- lint / audit / retry / partial rebuild
- reviewed knowledge promotion 强化
- agent-facing views 稳定化

进入 `Phase 2` 的前提：

- `Phase 1` 四个 sprint 全部完成
- 核心 schema 无待决项
- 至少两类真实样本跑通闭环

## 5. 执行约束

### 5.1 状态主控约束

- 只有 Java 可以写：
  - `source_registry`
  - `snapshot_store`
  - `knowledge_state`
  - `knowledge_control_state`
- Python worker 只能产出 `artifact/result`
- 任一状态更新都必须带：
  - `source_ref`
  - `snapshot_ref`
  - `job_id`

### 5.2 Worker 契约约束

- Python 只能依赖公开 job contract
- Java 只能依赖公开 artifact/result contract
- 禁止跨边界共享私有运行时对象
- worker 失败必须返回：
  - `status`
  - `issues`
  - `retriable`
  - `partial_artifacts`

### 5.3 知识升级约束

- graph signal 默认是 `candidate`
- wiki 草稿默认是可审校投影
- 升级为稳定知识前必须具备：
  - 来源
  - coverage / scope
  - 聚合依据
  - 必要时人工确认
- 局部结论不能直接升级为主题级或领域级结论

### 5.4 Artifact 约束

- graph artifact、wiki artifact、agent-facing view 必须彼此可追踪
- 任一 artifact 必须能说明：
  - 来自哪个 source
  - 来自哪个 snapshot
  - 由哪个 job 生成
  - 当前是什么状态
- 禁止只保留最终文本，不保留依据

### 5.5 DoD

每个任务完成都必须同时满足：

- schema 或接口文档存在
- 至少一个真实样例存在
- 至少一个正向测试和一个失败场景测试存在
- 来源追踪关系能走通
- 文档同步到 spec / plan / schema 说明

### 5.6 测试约束

- 每个 sprint 至少保留一条端到端样例
- 每个核心模块至少有一条“失败不破坏已有状态”的测试
- 增量能力未测试前，不宣称支持局部更新
- coverage / scope 未落地前，不输出高层稳定知识结论

### 5.7 文档同步约束

- 实现影响 schema、状态流转、artifact 格式时，必须同步更新文档
- 如果实现发现 spec 不合理，先改 spec，再改实现
- sprint 完成后必须回写状态：
  - 完成
  - 推迟
  - 拆分
  - 放弃

### 5.8 范围控制约束

- `Phase 1` 不做复杂检索、复杂 topic routing、复杂 UI
- `Phase 1` 只追求最小闭环和稳定边界
- `Phase 2` 只强化增量维护和治理，不另起炉灶
- 私有模型能力不提前侵入 `Phase 1` 主链路

## 6. 验收总表

计划完成时至少应满足：

- Java 是唯一主状态持有者
- Python worker 通过稳定契约交付处理结果
- source -> snapshot -> artifact -> state -> view 全链路可追溯
- graph/wiki 不直接冒充稳定高层知识
- `Codex` / `Trae` 可通过统一 views 消费知识底座
