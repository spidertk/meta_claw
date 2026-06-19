# Agent 执行抽象风险审计与改进方案

> 日期：2026-06-17  
> 范围：`meta-claw-core` AgentEngine SPI、`SpringAiAlibabaAgentEngine`、相关子系统与测试  
> 依据：`docs/superpowers/specs/2026-06-15-agent-execution-abstraction-design.md` 第 8 节风险与第 1.2 节不足点，以及当前代码实现  
> 目标：把审计发现的生产可用性缺口转化为可执行、可验证的改进计划。

---

## 1. 审计结论摘要

### 1.1 当前基线状态

- `./init.sh` 已于 2026-06-17 真实通过：9 个 reactor 模块全部 SUCCESS，`meta-claw-core` 96 个测试全部通过。
- AgentEngine SPI Phase 0~6 已全部编码完成：
  - `AgentEngine` / `AgentEngineFactory` / `NativeAgentEngine` / `SpringAiAlibabaAgentEngine`
  - `ReactAgentFactory`、`SpiMessageConverter`、`SaaMultiAgentFactory`
  - `MetaClawHitlHook`、`MetaClawAgentMetricsHook`、`MetaClawModelMetricsHook`
  - `VesselCheckpointSaver` 文件持久化
- 但上述实现几乎全部由 Mockito 单元测试覆盖，**未经过真实 LLM 端到端验证**。

### 1.2 风险分级结论

| 级别 | 含义 | 数量 |
|------|------|------|
| 🔴 P0 | 影响 `agent_engine: alibaba` 生产可用，必须先修 | 4 |
| 🟡 P1 | 明显影响可维护性或安全性，应尽早处理 | 7 |
| 🟢 P2 | 优化与架构债，可在主链路稳定后推进 | 6 |

---

## 2. 文档第 8 节风险改进方案

### 2.1 风险 1：Spring AI 1.1.8 与 SAA 1.1.2.3 二进制不兼容

| 项目 | 内容 |
|------|------|
| 当前状态 | ⚠️ 部分缓解 |
| 证据 | `pom.xml:47-51,83-90` 显式声明版本并导入两个 BOM；`AlibabaEngineSmokeTest` 验证 `ReactAgent.call(...)` 可编译运行 |
| 当前影响 | 编译期与基础 smoke test 通过，真实运行时仍可能触发 `NoSuchMethodError` / `NoClassDefFoundError` |
| 是否验证 | ✅ 编译 + smoke 单元测试；❌ 未用真实 LLM |

#### 改进动作

1. **受控真实 LLM 验证（P0）**
   - 在本地配置有效 API Key（OpenAI / Moonshot / DashScope 均可）。
   - 创建一个 `agent_engine: alibaba` 的 Vessel，分别跑通：
     - 单轮无工具对话
     - 带本地 `calculator` 工具的多轮 tool-call 对话
     - 流式对话（验证 `reasoningContent` 与 `usage`）
   - 记录返回的 `AssistantMessage.metadata` 结构与 SAA 行为，固化到 `claude-progress.md`。

2. **依赖收敛监控（P1）**
   - 在根 `pom.xml` 增加 `maven-enforcer-plugin` 规则，要求 SAA 传递依赖的 Spring AI 包版本必须与 `${spring-ai.version}` 一致。
   - 在 CI / `./init.sh` 中增加 `mvn dependency:tree -pl meta-claw-core | grep 'spring-ai' | sort | uniq` 检查。

3. **版本升级预案（P1）**
   - 跟踪 SAA GitHub release notes；一旦出现 1.1.2.4+，在独立分支升级并跑一次真实 LLM 验证后再合并。

---

### 2.2 风险 2：`ToolResponseMessage.toolCallId` 错误导致 SAA 循环失败

| 项目 | 内容 |
|------|------|
| 当前状态 | ✅ SAA 路径已缓解；⚠️ 原生路径仍有隐患 |
| 证据 | `SpiMessageConverter.java:47-52,84-96` 已正确解析 `toolCallId`；但 `LlmClientManager.java:470-474` 在 native 路径仍把 tool 消息写死为 `id/name = "tool"` |
| 当前影响 | SAA 路径 tool result 可正确回注；native 路径多轮 tool-call 可能匹配失败 |
| 是否验证 | ✅ SAA 路径单测；❌ native 路径未验证 |

#### 改进动作

1. **统一 message 转换（P1）**
   - 修改 `LlmClientManager.toSpringMessage()`，使其在 role=tool 时同样解析 JSON 中的 `toolCallId` / `toolName` / `result`，与 `SpiMessageConverter` 行为一致。
   - 或者，让 `LlmClientManager` 直接复用 `SpiMessageConverter`，避免两处维护两套转换逻辑。

2. **补充 native 路径测试（P1）**
   - 新增 `LlmClientManagerTest` 或扩展 `AgentExecutorTest`，验证多轮 tool-call 中 tool result 的 `toolCallId` 与 assistant tool_calls 的 id 一致。
   - 测试场景：assistant 返回两个 tool_calls → 分别执行 → 注入两条 tool result → assistant 能继续生成最终答案。

---

### 2.3 风险 3：SAA Hook 全名唯一性冲突

| 项目 | 内容 |
|------|------|
| 当前状态 | ✅ 已缓解 |
| 证据 | `ReactAgentFactory.java:42-44,79-101` 每个请求重新构建 `ReactAgent` 和 Hook 实例；Hook name 为固定常量 |
| 当前影响 | 通过“每请求独立实例”避免 name 冲突；代价是每次重建 Graph |
| 是否验证 | ✅ 单元测试 |

#### 改进动作

1. **保留当前缓解方案（无需改动）**
2. **未来引入缓存时的防护措施（P2）**
   - 若后续为 `ReactAgentFactory` 引入 Vessel 级或 provider 级缓存，必须为 Hook name 追加 `vesselId` 或请求 ID 后缀。
   - 在缓存 key 设计中把“有状态 Hook”与“无状态 Graph 编译产物”分离。

---

### 2.4 风险 4：流式 HITL 实现复杂

| 项目 | 内容 |
|------|------|
| 当前状态 | ❌ 未缓解（Alibaba 引擎） |
| 证据 | `SpringAiAlibabaAgentEngine.java:73-114` 只透传 content/reasoning/tool-call，未处理 HITL；`MetaClawHitlHook` 触发后会抛出 `HitlSuspendedException`，被外层 catch 后仅调用 `callback.onError(e)` |
| 当前影响 | `agent_engine: alibaba` + 流式 + HITL 会异常失败，无法收集用户决议继续执行 |
| 是否验证 | ❌ 未验证 |

#### 改进动作

**方案 A：短期 fallback（推荐先落地，P0）**

- 在 `VesselRuntime.chatStream()` 中，若当前 Vessel 配置 `agent_engine: alibaba` 且 HITL 策略非空，则自动 fallback 到 `native` 引擎的 `StreamingAgentExecutor`。
- 在 `SpringAiAlibabaAgentEngine.executeStream()` 中增加显式检查：如果 `HitlSubSystem` 配置会触发审批，直接抛 `UnsupportedOperationException` 并提示“流式 HITL 请使用 native 引擎”。
- 优点：简单、安全、不破坏现有行为。
- 缺点：Alibaba 引擎无法使用流式 HITL。

**方案 B：长期完整实现（P1）**

- 在 `streamAgentMessages()` 中捕获 `HitlSuspendedException`。
- 调用 `callback.onHitlSuspend(ticket)`，让 CLI / Gateway 层收集 `ApprovalResolution`。
- 将决议后的 tool result 注入 messages，再次调用 `agent.call(messages, config)` 或走 checkpoint 恢复路径。
- 需要新增测试 `SpringAiAlibabaAgentEngineStreamHitlTest`。

**建议**：先实施方案 A 作为保护，再逐步推进方案 B。

---

### 2.5 风险 5：Prompt 协议差异

| 项目 | 内容 |
|------|------|
| 当前状态 | ⚠️ 部分缓解 |
| 证据 | `VesselRuntime` 把渲染后的 system prompt 作为 `SpiMessage.system(...)` 放进 messages；`ReactAgentFactory.buildSingleAgent` 向 SAA 传入 `systemPrompt = ""` |
| 当前影响 | 两个引擎都能拿到完整 messages，基本可用；但 SAA 引擎未使用 `systemPrompt` / `instruction` 字段，损失部分 prompt 工程能力 |
| 是否验证 | ✅ SystemPromptBuilderTest；❌ 真实 LLM 上未验证两种引擎等价性 |

#### 改进动作

1. **SAA 引擎使用 systemPrompt / instruction（P1）**
   - 在 `ReactAgentFactory.buildSingleAgent()` 中，从 `request.getMessages()` 提取 system 消息内容，传给 `ReactAgent.builder().systemPrompt(...)`。
   - 子 Agent 的 `systemPrompt` 已来自 `VesselAgentConfig.systemPrompt`，无需改动。
   - 保留 `messages` 中仍包含 system 消息的方式，作为兼容兜底。

2. **PromptVars 映射为 SAA instruction（P2）**
   - 在 `PromptComposer` 输出中增加“SAA instruction”字段，把除 system 外的变量（如 `tools`、`skills`、`preferences`）拼接为 instruction。
   - 在 `ReactAgentFactory` 中把该 instruction 传给 `ReactAgent.builder()`（若 SAA builder 支持）；否则继续通过 messages 传递。

---

### 2.6 风险 6：引入 SAA 后依赖体积增大

| 项目 | 内容 |
|------|------|
| 当前状态 | ⚠️ 部分缓解 |
| 证据 | `meta-claw-core/pom.xml:91-98` 仅引入 `agent-framework` / `graph-core`；但 `spring-ai-alibaba-graph-core` 传递拉入 `spring-ai-deepseek`、`spring-ai-zhipuai`、`spring-ai-rag` 等 |
| 当前影响 | 核心包已控制直接依赖，但 SAA 传递依赖仍带来未使用的类与启动扫描开销 |
| 是否验证 | ✅ 依赖树检查；❌ 无启动耗时/包体积基线 |

#### 改进动作

1. **清理无用传递依赖（P1）**
   - 对 `spring-ai-alibaba-graph-core` 中不需要的模块增加 `<exclusion>`，例如：
     - `spring-ai-deepseek`
     - `spring-ai-zhipuai`
     - `spring-ai-rag`
     - `spring-ai-vector-store`
   - 每次排除后重新执行 `./init.sh` 确认编译与测试通过。

2. **建立依赖监控（P2）**
   - 在 `init.sh` 中加入 `mvn dependency:analyze -pl meta-claw-core` 检查未使用依赖。
   - 记录 `mvn dependency:tree` 输出到 `.meta-claw/dependency-baseline.txt`，作为后续版本升级对比基线。

---

## 3. 第 1.2 节资深用户不足点改进方案

| # | 不足点 | 状态 | 改进动作 | 优先级 |
|---|--------|------|----------|--------|
| 1 | 多 Agent 编排已落地，但真实 LLM 端到端验证仍待补充 | 成立 | 配置有效 API Key，创建 sequential / parallel / routing 三种 Vessel，各跑通一次真实对话；记录结果到 `claude-progress.md` | P0 |
| 2 | Alibaba 引擎缺少真实 LLM 端到端验证 | 成立 | 同风险 1 的受控验证；覆盖同步、流式、tool-call 三种场景 | P0 |
| 3 | HITL 流式路径未完整覆盖 | 成立 | 先实施方案 A fallback；再补充 `SpringAiAlibabaAgentEngineStreamHitlTest` | P0 |
| 4 | Checkpoint 持久化已出详细设计，尚未编码实施 | 不再成立 | 已实现；下一步补充真实 LLM 的 checkpoint 恢复验证 | P1 |
| 5 | 工具执行层未完全解耦 | 成立 | 按文档第 9 章推进 `ExecutableTool` SPI；先改 `ToolSubSystem.getExecutableTools()` 与 `AgentExecutor` | P2 |
| 6 | ReactAgentFactory 缺少实例缓存 | 成立 | 引入按 `ProviderConfig` 的 `ChatModel` 缓存，以及按 Vessel 的 `CompiledGraph` / `ReactAgent` 缓存；保证 Hook TaskContext 不串用 | P2 |
| 7 | Metrics Hook 的 token usage 依赖 SAA 内部 metadata | 成立 | 真实 LLM 验证后确认 metadata 结构；必要时增加 `ModelInterceptor` 或从 `ChatResponse` 取 usage | P1 |
| 8 | SAA 版本依赖风险持续存在 | 成立 | 依赖收敛监控 + 版本升级预案（见风险 1） | P1 |
| 9 | 编排配置类型安全不足 | 成立 | 将 `AgentFlowConfig.mode` 改为 `AgentFlowMode` 枚举字段；配置 SnakeYAML 大小写不敏感反序列化 | P1 |
| 10 | Prompt 协议差异未完全弥合 | 成立 | SAA 引擎使用 systemPrompt / instruction（见风险 5） | P1 |
| 11 | CLI/Gateway 层 engine 切换验收不足 | 成立 | 在 CLI 真实启动路径中验证 `agent_engine: alibaba` 的聊天、流式、HITL fallback | P1 |

---

## 4. 额外发现风险改进方案

### 4.1 线程安全

| 风险点 | 改进动作 | 优先级 |
|--------|----------|--------|
| `VesselCheckpointSaver.lockMap` 只增不减 | 在 `clear()` 或定期任务中移除已清空的锁；或使用 Guava `CacheBuilder` 设置过期 | P2 |
| `LlmClientProviderManager` 的 `initialized` / `allProviders` 无显式 happens-before | 将 `initialized` 改为 `AtomicBoolean`，`allProviders` 使用 `volatile` 或 `ConcurrentHashMap` | P2 |
| `MetaClawHitlHook.afterModel` 同步抛异常 | 改为 `return CompletableFuture.failedFuture(new HitlSuspendedException(...))` | P1 |

### 4.2 异常处理

| 风险点 | 改进动作 | 优先级 |
|--------|----------|--------|
| Alibaba 流式失败时异常处理不当 | 分离异常传播与回调，确保 `callback.onError` 自身抛异常时原始异常仍被记录 | P1 |
| `AgentExecutor` 超过 `maxSteps` 抛通用 `RuntimeException` | 定义 `MaxStepsExceededException` 等专用异常 | P2 |
| 工具执行异常被吞并转成字符串 | 增加 `recordToolError` 指标，并考虑让致命异常向上传播 | P2 |

### 4.3 配置验证

| 风险点 | 改进动作 | 优先级 |
|--------|----------|--------|
| `AgentFlowConfig.mode` 是 `String` | 改为 `AgentFlowMode` 枚举；SnakeYAML 大小写不敏感反序列化 | P1 |
| `VesselConfigBundle.getAgentEngine()` 不校验取值 | 在 `VesselConfigLoader` 或 bundle 构建时校验，给出可用引擎列表 | P1 |
| `VesselConfig.ToolConfig.exclude` 已定义但未被使用 | 在 `ToolSubSystem.getToolCallbacks()` 中应用排除名单，并补充测试 | P1 |
| `SaaMultiAgentFactory` 的 `fallbackAgent` 未校验 | 构建 `LlmRoutingAgent` 前校验 `fallbackAgent` 是否属于 `subAgents` | P1 |

### 4.4 资源管理

| 风险点 | 改进动作 | 优先级 |
|--------|----------|--------|
| `VesselCheckpointSaver` 未对 `vesselId` / `threadId` 做路径安全检查 | 校验 ID 只能由 `[A-Za-z0-9_-]` 组成；或使用 `Path.normalize()` 后检查是否在根目录下 | P1 |
| `ProjectRootFinder` 找不到 `pom.xml` 时回退到 `user.dir` | 增加显式数据目录配置项，禁止隐式回退；或至少打印 ERROR 并建议配置 | P1 |
| `LlmClientManager.streamWithTools/chatStream` 使用 `blockLast()` 无超时 | 为 Flux 增加 `.timeout(Duration)` | P1 |
| `ReactAgentFactory` 每请求创建新的 `ChatModel` | 按 `ProviderConfig` 缓存 `ChatModel` / `ChatClient` | P2 |

### 4.5 测试覆盖

| 风险点 | 改进动作 | 优先级 |
|--------|----------|--------|
| 无真实 LLM 集成测试 | 增加 P1 受控集成测试，使用真实 API Key，可选触发 | P0 |
| `LlmClientManager.toSpringMessage` 的 tool 占位符 bug | 增加 `LlmClientManagerTest` 验证 tool 消息 `toolCallId` | P1 |
| 无 `ToolConfig.exclude` 测试 | 补充 `ToolSubSystemTest` 用例 | P1 |

### 4.6 API 兼容性

| 风险点 | 改进动作 | 优先级 |
|--------|----------|--------|
| `SpringAiAlibabaAgentEngine` 直接调用 SAA 专有签名 | 在引擎与 SAA 之间增加薄适配层，将 SAA API 变化隔离在单一类中 | P2 |
| `LlmClientManager` 对 `ChatClient.tools(Object...)` 的非 varargs 调用触发编译警告 | 显式使用 `Object[]` 或正确 varargs 形式 | P2 |

### 4.7 性能

| 风险点 | 改进动作 | 优先级 |
|--------|----------|--------|
| 每请求重新 build / compile ReactAgent | 引入 Vessel 级无状态部分缓存，或缓存已 compile 的 `CompiledGraph` | P2 |
| `VesselCheckpointSaver.list()` 每次读取并反序列化全部 checkpoint | 维护按 thread 的索引文件或限制 `list()` 返回数量 | P2 |
| `LlmClientManager.buildChatClient` 每次调用新建 `ChatClient` | 按 vessel / provider 缓存 `ChatClient` | P2 |

### 4.8 安全

| 风险点 | 改进动作 | 优先级 |
|--------|----------|--------|
| Checkpoint 文件以明文 JSON 保存对话状态 | 对 checkpoint 目录设置文件权限；敏感字段加密 | P2 |
| `LlmClientManager.logRequestParams` 在 DEBUG 下打印完整 messages | 默认不打印完整消息，或对消息内容做脱敏 | P2 |
| `ToolConfig.exclude` 未生效 | 见 4.3 | P1 |
| `ShellTool` 等本地工具可直接执行系统命令 | 对 `ShellTool` 默认要求审批；对参数做白名单校验 | P1 |

---

## 5. 建议实施路线图

### Phase A：生产可用性兜底（1~2 天）

| 任务 | 交付物 | 验证方式 |
|------|--------|----------|
| A1. Alibaba 引擎真实 LLM 端到端验证 | `claude-progress.md` 验证记录 | 真实 API Key 跑通同步/流式/tool-call |
| A2. Alibaba 流式 HITL fallback | `VesselRuntime` / `SpringAiAlibabaAgentEngine` 改动 + 测试 | `./init.sh` 通过；新增 `SpringAiAlibabaAgentEngineStreamHitlTest` |
| A3. 修复 `ToolConfig.exclude` 生效 | `ToolSubSystem` 改动 + 测试 | `ToolSubSystemTest` 新增排除用例；`./init.sh` 通过 |
| A4. `VesselCheckpointSaver` 路径校验 | `VesselCheckpointSaver` 改动 + 测试 | `VesselCheckpointSaverTest` 新增非法 ID 用例；`./init.sh` 通过 |

### Phase B：配置与健壮性（2~3 天）

| 任务 | 交付物 | 验证方式 |
|------|--------|----------|
| B1. `AgentFlowConfig.mode` 改为枚举 | `AgentFlowConfig`、`VesselConfigLoader`、模板更新 | `VesselConfigLoaderTest` 新增大小写不敏感用例；`./init.sh` 通过 |
| B2. `agentEngine` 配置加载校验 | `VesselConfigLoader` 或 `VesselConfigBundle` 校验逻辑 | 新增 `VesselConfigLoaderTest#invalidAgentEngine`；`./init.sh` 通过 |
| B3. 统一 message 转换，修复 `LlmClientManager` tool 占位符 | `LlmClientManager` 改动 + 测试 | 新增 `LlmClientManagerTest` tool 消息用例；`./init.sh` 通过 |
| B4. `MetaClawHitlHook` 返回 failed Future | `MetaClawHitlHook` 改动 + 测试 | `MetaClawHitlHookTest` 更新；`./init.sh` 通过 |
| B5. `fallbackAgent` 存在性校验 | `SaaMultiAgentFactory` 改动 + 测试 | `SaaMultiAgentFactoryTest` 新增非法 fallback 用例；`./init.sh` 通过 |

### Phase C：架构优化（后续迭代）

| 任务 | 交付物 | 验证方式 |
|------|--------|----------|
| C1. 引入 `ExecutableTool` SPI | `ExecutableTool`、`SpringAiToolCallbackAdapter`、`ToolSubSystem`、`AgentExecutor` 改造 | `./init.sh` 通过；新增适配器测试 |
| C2. `ReactAgentFactory` 实例 / ChatModel 缓存 | 缓存实现 + 并发测试 | `./init.sh` 通过；压测观察构建开销下降 |
| C3. SAA Prompt 协议对齐 | `ReactAgentFactory` 使用 `systemPrompt` / `instruction` | 真实 LLM 验证两种引擎输出等价性 |
| C4. 依赖收敛与监控 | `maven-enforcer-plugin`、`<exclusion>`、依赖基线 | `./init.sh` 通过；依赖树对比 |
| C5. 安全加固 | checkpoint 加密、DEBUG 日志脱敏、`ShellTool` 默认审批 | `./init.sh` 通过；安全 review |

---

## 6. 验收标准

1. **Phase A 完成后**：
   - 在受控环境（配置真实 API Key）下，`agent_engine: alibaba` 的 Vessel 能完成一次真实对话、一次真实 tool-call 对话、一次真实流式对话。
   - `./init.sh` 全量通过。
   - `agent_engine: alibaba` + 流式 + HITL 不再异常崩溃，而是明确 fallback 到 native 引擎或提示用户。

2. **Phase B 完成后**：
   - 配置拼写错误（如 `sequental`、`alibba`）在加载阶段即报错。
   - `ToolConfig.exclude` 能真正排除工具。
   - `LlmClientManager` 的 tool 消息携带正确 `toolCallId`。
   - `./init.sh` 全量通过。

3. **Phase C 完成后**：
   - `AgentExecutor` 不再直接依赖 `ToolCallback`。
   - `ReactAgentFactory` 高频请求下无明显重复构建开销。
   - 依赖树中不再包含未使用的 SAA 传递模块。
   - `./init.sh` 全量通过。

---

## 7. 风险与阻塞

| 风险 | 影响 | 缓解 |
|------|------|------|
| 真实 LLM 验证需要有效 API Key 与网络 | 可能无法在当前环境执行 | 使用用户提供的 Key；若无，则记录为未解决 blocker |
| SAA 版本升级导致 API 签名变化 | 改进方案中的部分代码可能需调整 | 所有 SAA 相关改动保持最小化并集中在一个薄适配层 |
| 引入缓存后 TaskContext 串用 | Hook 可能拿到过期上下文 | 缓存 key 不包含 TaskContext；Hook 在每次调用时重新 new |

---

## 8. 结论

当前 Agent 执行抽象在代码层面已经完整，但距离生产可用仍有明显差距：
- **真实 LLM 验证缺失**是最大的不确定性来源。
- **Alibaba 引擎流式 HITL 未实现**是直接影响可用性的 P0 问题。
- **配置验证、工具排除、路径安全**是容易被忽视但影响安全预期的 P1 问题。

建议按 Phase A → Phase B → Phase C 的顺序推进，每完成一个 Phase 都重新运行 `./init.sh` 并更新 `claude-progress.md` 与 `feature_list.json`。
