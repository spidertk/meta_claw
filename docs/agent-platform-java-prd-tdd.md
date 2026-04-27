# Agent Platform Java 版需求与技术设计

## 1. 背景

现有参考项目 `.rwa/expert_project` 与 `.rwa/CowAgent` 都已经验证了一类产品方向:

- 不是单一问答机器人，而是可长期运行的 Agent 平台
- 需要同时具备工具调用、技能扩展、长期记忆、知识管理、多通道接入能力
- 需要兼顾本地化运行、平台化扩展、以及面向业务场景的持续演进

本设计文档基于两个参考项目的优点，收敛出一个 **Java 技术栈** 的 Agent Platform 目标方案，用于后续产品立项、架构设计与实现排期。

## 2. 参考项目分析

### 2.1 expert_project 的主要特点

基于现有 graphify 报告与关键代码，`expert_project` 的核心优势集中在“抽象清晰”和“运行时边界明确”。

关键证据:

- 工具抽象以 `BaseTool` 为核心，接口简洁，适合统一注册与热替换
  参考: `.rwa/expert_project/expert/runtime/tools/base.py`
- 接入层以 `BaseAdapter` 统一外部消息格式，形成明确的 Gateway 边界
  参考: `.rwa/expert_project/expert/gateway/base.py`
- 运行时 `ExpertRuntime` 集中装配记忆、知识、技能、工具、Evo、Cron 等核心子系统
  参考: `.rwa/expert_project/expert/runtime/expert_runtime.py`
- CLI 入口 `ExpertApp` 与 Runtime 解耦，应用层职责清晰
  参考: `.rwa/expert_project/expert_cli/app.py`

从图谱结果看，`expert_project` 的 god nodes 包括:

- `BaseTool`
- `BaseAdapter`
- `GatewayMessage`
- `GlobalConfig`
- `OutboundMessage`

这说明它最强的部分不是“渠道多”，而是 **核心抽象收敛得好**。

### 2.2 CowAgent 的主要特点

`CowAgent` 的核心优势集中在“产品能力完整”和“平台化落地成熟”。

关键证据:

- `ChannelManager` 与 `channel_factory` 支持多通道并发运行、动态增删和统一生命周期管理
  参考: `.rwa/CowAgent/app.py`, `.rwa/CowAgent/channel/channel_factory.py`
- `ChatChannel` 提供统一消息处理骨架，把触发词、群聊/私聊、插件事件、回复发送串起来
  参考: `.rwa/CowAgent/channel/chat_channel.py`
- `Agent` 将工具、技能、记忆、上下文压缩、系统提示词重建放入同一执行模型
  参考: `.rwa/CowAgent/agent/protocol/agent.py`
- `PromptBuilder` 将工具、技能、记忆、知识、工作区上下文组织成稳定提示词结构
  参考: `.rwa/CowAgent/agent/prompt/builder.py`
- `SkillManager`、`MemoryManager` 已形成较完整的平台能力
  参考: `.rwa/CowAgent/agent/skills/manager.py`, `.rwa/CowAgent/agent/memory/manager.py`

从核心子集图谱看，`CowAgent` 的 god nodes 包括:

- `BaseTool`
- `ToolResult`
- `ChatChannel`
- `SkillManager`
- `ToolManager`
- `TaskStore`

这说明它最强的部分不是底层抽象优雅度，而是 **面向真实使用场景的系统完整度**。

## 3. 两个项目的优点提炼

### 3.1 expert_project 应继承的优点

- 核心接口边界清晰，适合做企业级重构与持续演进
- Runtime 负责装配，Application 负责入口，职责划分明确
- Gateway / Adapter / Tool / Knowledge / Memory 的模块边界较好
- 演进能力较强，适合加入可热插拔组件与灰度能力
- 更适合被 Java 重新实现为清晰的分层架构

### 3.2 CowAgent 应继承的优点

- 产品面完整，覆盖多通道、多模态、技能、CLI、记忆、知识、浏览器、安装与运维
- 通道接入和会话处理机制更贴近真实业务场景
- Prompt 组织、技能管理、上下文治理、记忆检索更成熟
- 具备更强的平台化和运营化特征
- 更适合作为需求侧与功能完整度的参考

### 3.3 综合结论

目标方案不应该简单复制任一项目，而应采用:

- `expert_project` 的架构抽象能力
- `CowAgent` 的产品能力覆盖

一句话总结:

**用 expert_project 的“骨架”，实现 CowAgent 的“完整能力”，并用 Java 技术栈做工程化重构。**

## 4. 产品目标

构建一个基于 Java 的 Agent Platform，支持:

- 多模型接入
- 多通道接入
- 工具系统
- 技能系统
- 长期记忆
- 知识库
- 多会话管理
- 多租户或多 Agent 配置
- 运维后台与可观测能力

平台既能作为:

- 单体 AI 助手产品
- 企业内部 Agent 中台
- 渠道机器人网关
- 面向场景的技能运行平台

## 5. 产品需求文档

### 5.1 目标用户

- 个人开发者
- 企业内部 AI 平台团队
- 需要多渠道客服/运营机器人的业务团队
- 需要私有化 Agent 平台的交付团队

### 5.2 核心场景

- 用户通过 Web、企业微信、飞书、钉钉、微信公众号等渠道发起任务
- Agent 自动理解任务并拆解步骤
- Agent 调用工具完成文件处理、知识检索、外部 API 操作、网页访问等任务
- Agent 利用长期记忆和知识库形成持续改进能力
- 运营人员通过后台管理技能、模型、渠道、租户、定时任务和日志

### 5.3 功能需求

#### 5.3.1 多通道接入

- 支持 Web Chat
- 支持企业微信、飞书、钉钉、微信公众号、终端
- 支持通道独立配置与启停
- 支持消息统一协议转换

#### 5.3.2 Agent Runtime

- 支持单轮对话与多步 Agent 执行
- 支持工具调用循环
- 支持最大步数、超时、失败重试、上下文裁剪
- 支持流式输出

#### 5.3.3 工具系统

- 支持内置工具注册
- 支持动态启停工具
- 支持按租户/Agent 配置工具可见性
- 支持工具权限控制、审批与审计
- 支持浏览器、HTTP、文件、Shell、知识检索、记忆检索、定时任务等工具

#### 5.3.4 技能系统

- 支持技能安装、启用、禁用、升级
- 支持从本地仓库、Git 仓库、技能市场安装
- 支持技能元数据、依赖、运行前校验
- 支持技能与工具、知识、记忆联动

#### 5.3.5 记忆系统

- 支持会话记忆
- 支持长期记忆
- 支持用户级、共享级、租户级记忆作用域
- 支持关键词检索与向量检索混合搜索
- 支持对话摘要和记忆压缩

#### 5.3.6 知识库系统

- 支持知识导入、分块、索引、检索
- 支持文档元数据管理
- 支持图谱或引用关系视图
- 支持 Agent 在推理中调用知识检索

#### 5.3.7 管理后台

- 管理模型配置
- 管理通道配置
- 管理技能配置
- 管理工具权限
- 查看会话、日志、调用链、成本统计

#### 5.3.8 可观测与安全

- 记录 Prompt、工具调用、错误、耗时、Token 消耗
- 支持审计日志
- 支持敏感工具审批
- 支持 RBAC
- 支持脱敏与密钥管理

### 5.4 非功能需求

- 可私有化部署
- 支持高并发消息接入
- 支持横向扩展
- 模块可插拔
- 核心链路可观测
- 关键数据可追溯
- 对外接口稳定可版本化

## 6. 技术设计文档

### 6.1 技术栈

建议采用:

- Java 21
- Spring Boot 3
- Spring WebFlux
- Spring Modulith 或基于 Gradle/Maven 的多模块架构
- PostgreSQL
- Redis
- pgvector 或 Elasticsearch / OpenSearch
- RabbitMQ 或 Kafka
- MinIO / S3
- Quartz
- Micrometer + Prometheus + Grafana
- OpenTelemetry

### 6.2 系统架构分层

建议拆成以下逻辑层:

1. 接入层
2. 会话与协议层
3. Agent Runtime 层
4. Tool / Skill 执行层
5. Memory / Knowledge 层
6. 管理与运维层
7. 基础设施层

### 6.3 模块设计

#### 6.3.1 `agent-gateway`

职责:

- 接收各通道消息
- 统一转成内部消息协议
- 将回复适配回各通道

参考来源:

- `expert_project` 的 `BaseAdapter`
- `CowAgent` 的 `ChatChannel` 和 `channel_factory`

建议接口:

- `ChannelAdapter`
- `InboundMessage`
- `OutboundMessage`
- `ChannelRegistry`
- `ChannelLifecycleManager`

#### 6.3.2 `agent-runtime`

职责:

- 管理 Agent 的执行状态机
- 组织 Prompt
- 管理多步推理与工具调用循环
- 处理上下文压缩、步数限制、错误恢复

建议核心对象:

- `AgentRuntime`
- `AgentSession`
- `AgentExecutor`
- `ActionPlanner`
- `StepContext`
- `RuntimePolicy`

设计原则:

- 借鉴 `expert_project` 的 Runtime 装配模式
- 吸收 `CowAgent` 的上下文裁剪和动态 Prompt 重建能力

#### 6.3.3 `agent-tooling`

职责:

- 工具定义
- 工具注册
- 工具配置
- 工具执行
- 工具权限校验与审计

建议核心接口:

- `ToolDefinition`
- `ToolExecutor`
- `ToolRegistry`
- `ToolPermissionService`
- `ToolAuditService`

设计要求:

- 工具实例创建与配置解耦
- 支持同步/异步工具
- 支持人工审批工具
- 支持沙箱工具执行

#### 6.3.4 `agent-skills`

职责:

- 技能元数据管理
- 技能加载
- 技能启停
- 技能依赖校验
- 技能市场对接

建议核心接口:

- `SkillLoader`
- `SkillManager`
- `SkillRepository`
- `SkillInstaller`
- `SkillRequirementChecker`

设计要求:

- 技能要与 Runtime 解耦
- 技能只提供规则、文档、模板和附属资源
- 工具仍由 Tooling 层统一调度

#### 6.3.5 `agent-memory`

职责:

- 对话记忆存储
- 摘要压缩
- 混合检索
- 多作用域记忆管理

建议核心对象:

- `MemoryManager`
- `ConversationStore`
- `MemoryChunk`
- `MemoryIndexer`
- `MemorySearchService`
- `MemoryFlushService`

设计要求:

- 支持 shared / tenant / user / session 四级作用域
- 检索同时支持关键词与向量
- 支持异步索引构建

#### 6.3.6 `agent-knowledge`

职责:

- 知识文档导入
- 切片与索引
- 引用关系管理
- 知识检索
- 图谱增强

建议核心对象:

- `KnowledgeDocument`
- `KnowledgeChunk`
- `KnowledgeIndexer`
- `KnowledgeSearchService`
- `KnowledgeGraphService`

#### 6.3.7 `agent-console`

职责:

- 管理后台
- 会话查询
- 成本统计
- 通道管理
- 技能与工具配置

前端建议:

- React + TypeScript

后端接口:

- REST + SSE / WebSocket

### 6.4 关键数据模型

#### 6.4.1 会话

- `agent_session`
- `session_message`
- `session_tool_call`
- `session_tool_result`

#### 6.4.2 记忆

- `memory_chunk`
- `memory_embedding`
- `memory_scope_binding`
- `memory_summary`

#### 6.4.3 知识

- `knowledge_document`
- `knowledge_chunk`
- `knowledge_relation`
- `knowledge_tag`

#### 6.4.4 技能

- `skill_package`
- `skill_version`
- `skill_installation`
- `skill_runtime_binding`

#### 6.4.5 工具

- `tool_definition`
- `tool_config`
- `tool_permission_policy`
- `tool_audit_log`

### 6.5 核心流程设计

#### 6.5.1 消息处理流程

1. 通道收到消息
2. Gateway 转成统一 `InboundMessage`
3. SessionService 定位或创建会话
4. Runtime 构建上下文
5. Runtime 调用模型
6. 若模型触发工具，则进入 ToolExecutor
7. 工具结果回写到上下文
8. Runtime 继续直到产出最终回复
9. OutboundMessage 发送回通道
10. 同步写入日志、记忆、指标

#### 6.5.2 技能执行流程

1. Runtime 根据用户意图命中技能
2. SkillManager 装载技能元数据与说明
3. PromptBuilder 将技能内容注入上下文
4. Agent 调用工具完成技能要求
5. 执行结果与过程进入审计与会话记录

#### 6.5.3 记忆写入流程

1. 会话结束或达到阈值
2. SummaryService 生成摘要
3. Chunker 进行切片
4. EmbeddingService 生成向量
5. SearchIndex 写入关键词索引和向量索引
6. 后续 Runtime 检索时按作用域召回

### 6.6 安全设计

- 所有外部工具必须经过权限校验
- Shell、Browser、File 等高风险工具支持审批策略
- 敏感配置进入 Secret Manager，不直接入库明文
- 模型请求与工具请求都要具备审计链路
- 支持租户隔离、工作区隔离、文件访问白名单

### 6.7 可观测设计

核心指标:

- 请求数
- 会话数
- Agent 步数
- 工具调用成功率
- 模型耗时
- Token 消耗
- 记忆召回命中率
- 知识检索命中率

核心日志:

- 通道接入日志
- Runtime 步骤日志
- 工具调用日志
- 审批日志
- 错误与超时日志

### 6.8 部署设计

部署形态:

- 单机版
- Docker Compose
- Kubernetes

服务拆分建议:

- `gateway-service`
- `runtime-service`
- `memory-service`
- `knowledge-service`
- `console-service`
- `scheduler-service`

初期为了降低复杂度，也可先采用:

- 单体 Spring Boot + 模块化架构

当规模增长后再拆服务。

## 7. 里程碑规划

### Phase 1: MVP

- Web 通道
- 单模型接入
- 基础 Agent Runtime
- 文件/HTTP/Shell 三类工具
- 基础技能系统
- 会话存储
- 简单记忆检索

### Phase 2: 平台化

- 多通道接入
- 技能市场
- 完整记忆系统
- 知识库导入与检索
- 管理后台
- 审批与审计

### Phase 3: 企业化

- 多租户
- 权限体系
- 工作流编排
- 插件市场
- 监控告警
- 高可用部署

## 8. 最终方案结论

面向 Java 技术栈的目标平台应采用以下组合策略:

- 架构上学习 `expert_project`
  - 清晰抽象
  - 明确边界
  - Runtime 装配中心化
- 能力上学习 `CowAgent`
  - 多通道
  - 技能系统
  - Prompt 组织
  - 记忆与知识能力
  - 更完整的产品闭环

最终产品定位:

**一个基于 Java 的、面向多渠道与多场景的 Agent Platform。**

它不是简单聊天机器人，而是:

- 可运行
- 可扩展
- 可审计
- 可运营
- 可私有化

的企业级智能体基础平台。
