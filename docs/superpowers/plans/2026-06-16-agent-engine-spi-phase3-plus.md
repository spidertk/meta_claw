# Agent Engine SPI 后续实施计划（Phase 3 ~ Phase 6）

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在主设计文档 `2026-06-15-agent-execution-abstraction-design.md` 已确定的 `AgentEngine` SPI 基础上，继续推进 Phase 3 及后续工作：让 `agent_engine: alibaba` 的 Vessel 支持流式输出、Metrics Hook、HITL Hook、多 Agent 编排与 checkpoint 持久化。

**Current baseline:** Phase 0+1+2 已完成且 `./init.sh` 通过（2026-06-16）。`AgentEngine` SPI、`AgentEngineFactory`、`NativeAgentEngine`、`SpringAiAlibabaAgentEngine`（同步 call）、`SpiMessageConverter`、`ReactAgentFactory` 已落地，`VesselRuntime` 已通过工厂按配置路由。

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring AI 1.1.8, Spring AI Alibaba 1.1.2.3, Maven, JUnit 5, Mockito, Lombok, Reactor.

---

## 文件结构总览（新增/修改）

| 文件 | 操作 | 职责 |
|------|------|------|
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngine.java` | 修改 | Phase 3：实现 `executeStream()` |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/ReactAgentFactory.java` | 修改 | Phase 3：注册 Metrics Hook；Phase 4：注册 HITL Hook |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/alibabahook/MetaClawAgentMetricsHook.java` | 创建 | Phase 3：任务级 Metrics Hook（beforeAgent/afterAgent） |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/alibabahook/MetaClawModelMetricsHook.java` | 创建 | Phase 3：模型级 Metrics Hook（beforeModel/afterModel） |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/alibabahook/MetaClawHitlHook.java` | 创建 | Phase 4：HITL Model Hook |
| `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngineStreamTest.java` | 创建 | Phase 3：流式执行测试 |
| `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/alibabahook/MetaClawAgentMetricsHookTest.java` | 创建 | Phase 3：任务级 Metrics Hook 测试 |
| `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/alibabahook/MetaClawModelMetricsHookTest.java` | 创建 | Phase 3：模型级 Metrics Hook 测试 |
| `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/alibabahook/MetaClawHitlHookTest.java` | 创建 | Phase 4：HITL Hook 测试 |
| `init.sh` | 修改 | 将新增 P0 测试纳入基线 |
| `feature_list.json` | 修改 | 更新 `agent-engine-001` 证据 |
| `claude-progress.md` | 修改 | 记录 Phase 3+ 进度 |
| `docs/superpowers/specs/2026-06-15-agent-execution-abstraction-design.md` | 修改 | 维护已实现/未实现进度 |

---

## Phase 3：流式 + Metrics Hook（已完成）

### Task 1: 接入 SAA 流式输出

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngine.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngineStreamTest.java`

**目标：** 实现 `SpringAiAlibabaAgentEngine.executeStream()`，将 SAA `ReactAgent.streamMessages(List<Message>)` 的流式事件透传到 `SpiStreamingCallback`，最终返回 `Reply`。

SAA API（已确认）：
- `com.alibaba.cloud.ai.graph.agent.Agent.streamMessages(List<Message>)` 返回 `reactor.core.publisher.Flux<org.springframework.ai.chat.messages.Message>`
- 每个 `Message` 可能是 `AssistantMessage`（含 content / toolCalls / metadata）或 `ToolResponseMessage`

实现要点：
- 使用 `SpiMessageConverter.toSpringMessages(request.getMessages())` 准备输入
- 调用 `agent.streamMessages(messages)`
- 订阅 Flux，对每条 `AssistantMessage`：
  - 从 `message.getMetadata()` 中尝试读取 `reasoningContent`，若有则回调 `onReasoningChunk`
  - 将 `message.getText()` 回调 `onChunk`
  - 若 `message.hasToolCalls()` 为真，将每个 `AssistantMessage.ToolCall` 转成 `SpiToolCall` 并回调 `onToolCall`
- 忽略 `ToolResponseMessage`（由 SAA 内部处理）
- 累积最后一条 `AssistantMessage` 的文本，封装为 `Reply(ReplyType.TEXT, text)` 返回
- 若流式过程中发生 `GraphRunnerException`，通过 `callback.onError(...)` 或抛出 `RuntimeException` 传播

回调约定对齐 native 引擎：
- `onReasoningChunk(String chunk)`：thinking 内容片段
- `onChunk(String chunk)`：最终答案片段
- `onToolCall(SpiToolCall tc)`：工具调用通知
- `onUsage(Usage usage)`：SAA `streamMessages` 不直接暴露 usage，当前阶段可传 `null` 或从最后一条消息 metadata 中提取

- [x] **Step 1.1: 修改 `SpringAiAlibabaAgentEngine.executeStream()`**

- [x] **Step 1.2: 创建流式单元测试**

  使用 Mockito 模拟 `ReactAgentFactory` 与 `ReactAgent`：
  - 构造一个 `Flux<Message>`，包含多条 `AssistantMessage`
  - 验证 `callback.onChunk()` 被按顺序调用
  - 验证带 tool calls 的 assistant message 触发 `callback.onToolCall()`
  - 验证方法返回的 `Reply` 包含合并后的最终文本

- [x] **Step 1.3: 编译验证**

  ```bash
  mvn clean compile -pl meta-claw-core -am -q
  ```

  Expected: `BUILD SUCCESS`。

- [x] **Step 1.4: Commit**

  ```bash
  git add meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngine.java \
          meta-claw-core/src/test/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngineStreamTest.java
  git commit -m "feat(engine): add streaming support to SpringAiAlibabaAgentEngine"
  ```

---

### Task 2: 实现 `MetaClawAgentMetricsHook`（任务级指标）

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/alibabahook/MetaClawAgentMetricsHook.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/alibabahook/MetaClawAgentMetricsHookTest.java`

**目标：** 在 SAA Agent 执行前后记录任务级指标：任务开始时间、任务完成数、步数、任务时长。

SAA API（已确认）：
- `com.alibaba.cloud.ai.graph.agent.hook.AgentHook` 是抽象类
- `beforeAgent(OverAllState, RunnableConfig)` 和 `afterAgent(OverAllState, RunnableConfig)` 返回 `CompletableFuture<Map<String,Object>>`
- 实现类需 `extends AgentHook`
- 不修改状态时可返回 `CompletableFuture.completedFuture(null)`

实现要点：
- 构造函数接收 `TaskContext ctx`
- `beforeAgent`：调用 `ctx.markTaskStart()`（如存在）或记录 `startTime = System.nanoTime()` 到本地字段
- `afterAgent`：
  - 计算 `durationMs`
  - 通过 `MetricsRecorder` 记录：
    - `agent.task.completed` 计数器（vessel 标签）
    - `agent.steps` 计数器（vessel 标签，值取 `ctx.getCurrentStep()`）
    - `agent.task.duration` 计时器（vessel 标签）
  - 返回 `CompletableFuture.completedFuture(null)`

- [x] **Step 2.1: 创建 `MetaClawAgentMetricsHook`**

- [x] **Step 2.2: 创建单元测试**

  使用 Mockito 模拟 `TaskContext` 与 `MetricsRecorder`，注入 `ReflectionTestUtils`：
  - 验证 `beforeAgent` 调用 `ctx.markTaskStart()`
  - 验证 `afterAgent` 调用 `MetricsRecorder` 的对应方法

- [x] **Step 2.3: 编译验证**

  ```bash
  mvn clean compile -pl meta-claw-core -am -q
  ```

- [x] **Step 2.4: Commit**

  ```bash
  git add meta-claw-core/src/main/java/meta/claw/core/runtime/engine/alibabahook/MetaClawAgentMetricsHook.java \
          meta-claw-core/src/test/java/meta/claw/core/runtime/engine/alibabahook/MetaClawAgentMetricsHookTest.java
  git commit -m "feat(engine): add MetaClawAgentMetricsHook for SAA task-level metrics"
  ```

---

### Task 3: 实现 `MetaClawModelMetricsHook`（模型级指标）

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/alibabahook/MetaClawModelMetricsHook.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/alibabahook/MetaClawModelMetricsHookTest.java`

**目标：** 在 SAA 每次模型调用前后记录 LLM 延迟、Token 消耗、工具调用次数。

SAA API（已确认）：
- `com.alibaba.cloud.ai.graph.agent.hook.ModelHook` 是抽象类
- `beforeModel(OverAllState, RunnableConfig)` 和 `afterModel(OverAllState, RunnableConfig)` 返回 `CompletableFuture<Map<String,Object>>`
- 实现类需 `extends ModelHook`

实现要点：
- 构造函数接收 `TaskContext ctx`
- `beforeModel`：记录 `modelStartNanos = System.nanoTime()`
- `afterModel`：
  - 计算 `latencyMs`
  - 从 `OverAllState.data()` 中读取 `messages` 键（类型 `List<Message>`），取最后一条
  - 若为 `AssistantMessage`：
    - 尝试从 message metadata 读取 usage（prompt/completion/total tokens）
    - 记录 `agent.llm.latency` 计时器（vessel 标签）
    - 记录 `agent.llm.tokens` 计数器（vessel、type 标签：`prompt`/`completion`/`total`）
    - 若有 toolCalls，遍历并记录 `agent.tool.calls` 计数器（vessel、tool 标签）
  - 返回 `CompletableFuture.completedFuture(null)`

> **说明：** 若 `OverAllState` 中无法直接获取 usage，可先记录 latency 与 tool calls；token usage 的精确采集可后续通过 `ModelInterceptor` 增强。

- [x] **Step 3.1: 创建 `MetaClawModelMetricsHook`**

- [x] **Step 3.2: 创建单元测试**

  使用 Mockito 模拟 `OverAllState`、`TaskContext`、`MetricsRecorder`：
  - 验证 `beforeModel` 记录开始时间
  - 验证 `afterModel` 在包含 AssistantMessage + tool calls 时记录 latency、tokens、tool calls
  - 验证空消息列表时不报错

- [x] **Step 3.3: 编译验证**

  ```bash
  mvn clean compile -pl meta-claw-core -am -q
  ```

- [x] **Step 3.4: Commit**

  ```bash
  git add meta-claw-core/src/main/java/meta/claw/core/runtime/engine/alibabahook/MetaClawModelMetricsHook.java \
          meta-claw-core/src/test/java/meta/claw/core/runtime/engine/alibabahook/MetaClawModelMetricsHookTest.java
  git commit -m "feat(engine): add MetaClawModelMetricsHook for SAA LLM/tool metrics"
  ```

---

### Task 4: 在 `ReactAgentFactory` 注册 Metrics Hook

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/ReactAgentFactory.java`

**目标：** 每个 Vessel 的 ReactAgent 构建时，自动注入 `MetaClawAgentMetricsHook` 与 `MetaClawModelMetricsHook`。

实现要点：
- 在 `ReactAgentFactory.build(TaskContext ctx)` 中：
  - 创建 `MetaClawAgentMetricsHook(ctx)`
  - 创建 `MetaClawModelMetricsHook(ctx)`
  - 在 `ReactAgent.builder()...hooks(hook1, hook2).build()` 中注册

- [x] **Step 4.1: 修改 `ReactAgentFactory.build()`**

- [x] **Step 4.2: 编译验证**

  ```bash
  mvn clean compile -pl meta-claw-core -am -q
  ```

- [x] **Step 4.3: Commit**

  ```bash
  git add meta-claw-core/src/main/java/meta/claw/core/runtime/engine/ReactAgentFactory.java
  git commit -m "feat(engine): register metrics hooks in ReactAgentFactory"
  ```

---

### Task 5: 把新增测试纳入 P0 基线并全量验证（已完成）

**Files:**
- Modify: `init.sh`

- [x] **Step 5.1: 在 `init.sh` 的 `-Dtest=` 列表追加新测试**

  将 `SpringAiAlibabaAgentEngineStreamTest`、`MetaClawAgentMetricsHookTest`、`MetaClawModelMetricsHookTest` 追加到 `-Dtest=` 列表（第 13 行与第 51 行同步）。

- [x] **Step 5.2: 运行 `./init.sh` 全量验证**

  ```bash
  ./init.sh
  ```

  Expected: 全仓编译 SUCCESS，P0 测试全部通过。

- [x] **Step 5.3: Commit**

  ```bash
  git add init.sh
  git commit -m "chore(init): include Phase 3 engine tests in P0 baseline"
  ```

---

## Phase 4：HITL Hook

### Task 6: 实现 `MetaClawHitlHook`

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/alibabahook/MetaClawHitlHook.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/alibabahook/MetaClawHitlHookTest.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngine.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/ReactAgentFactory.java`

**目标：** 将 meta-claw `HitlSubSystem` 接入 SAA AFTER_MODEL Hook，当模型返回 tool_calls 且命中审批策略时中断图执行；外部收集 `ApprovalResolution` 后通过 `resume()` 继续。

实现要点：
- `MetaClawHitlHook extends ModelHook`
- `afterModel(OverAllState, RunnableConfig)`：
  - 从 state 的 messages 中取最后一条 AssistantMessage
  - 若有 tool_calls，转成 `List<SpiToolCall>`
  - 调用 `HitlSubSystem.evaluate(spiToolCalls, ctx)`
  - 若 `eval.hasSuspensions()`，抛 `HitlSuspendedException(eval.getTicket())`
- `ReactAgentFactory.build()` 注册 `MetaClawHitlHook(ctx)`
- `SpringAiAlibabaAgentEngine.resume()`：
  - 把 `ApprovalResolution` 中 APPROVED/REJECTED 的 tool 结果注入 messages
  - 重新 `agent.call(messages)`

- [x] **Step 6.1: 创建 `MetaClawHitlHook`**
- [x] **Step 6.2: 创建单元测试**
- [x] **Step 6.3: 修改 `ReactAgentFactory` 注册 HITL Hook**
- [x] **Step 6.4: 实现 `SpringAiAlibabaAgentEngine.resume()`**
- [x] **Step 6.5: 编译验证与 `./init.sh` 全量验证**
- [x] **Step 6.6: Commit**

---

## Phase 4 完成注记

- `MetaClawHitlHook` 已注册到 `ReactAgentFactory`，Alibaba 引擎执行路径现已具备与 native 引擎一致的 HITL 中断/恢复能力。
- `SpringAiAlibabaAgentEngine.resume()` 在收到 `ApprovalResolution` 后，会为 ticket 中每个 item 执行 APPROVED 工具（或生成 REJECTED 占位结果），把 tool result 以 `SpiMessage.tool(...)` 形式追加到 messages，再调用 `ReactAgent.call(messages)` 继续 ReAct 循环。
- `./init.sh` 已纳入 `MetaClawHitlHookTest` 与 `SpringAiAlibabaAgentEngineTest#resumeExecutesApprovedToolAndContinues`；全量 P0 测试通过，core 68 个测试全部通过。

---

## Phase 5：多 Agent 编排

**目标：** 让 `agent_engine: alibaba` 的 Vessel 能够配置多个子 Agent，并选择 `sequential` / `parallel` / `routing` 等编排模式执行。

### Task 7: 配置模型扩展

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/config/VesselAgentConfig.java`
- Create: `meta-claw-core/src/main/java/meta/claw/core/config/AgentFlowConfig.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/config/VesselConfig.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/config/bundle/VesselConfigBundle.java`
- Modify: `meta-claw-vessel/src/main/resources/templates/vessel-config.tmpl.yaml`（或当前模板路径）
- Modify: `meta-claw-core/src/test/java/meta/claw/core/config/VesselConfigLoaderTest.java`

**目标：** 在 Vessel 配置中声明子 Agent 列表与编排模式。

YAML 设计示例：

```yaml
name: coding-assistant
agent_engine: alibaba
agents:
  - name: planner
    description: 负责把用户需求拆分为可执行步骤
    model: gpt-4o-mini
    system_prompt: You are a planner...
  - name: coder
    description: 负责编写代码
    model: gpt-4o
    system_prompt: You are a coder...
flow:
  mode: sequential   # sequential | parallel | routing
  # routing 模式特有
  routing_prompt: |
    Given the user request, route to one of: planner, coder.
  fallback_agent: planner
```

实现要点：
- `VesselAgentConfig` 字段：`name`、`description`、`model`、`systemPrompt`、`tools`（可选，子 Agent 专属工具名列表）。
- `AgentFlowConfig` 字段：`mode`（enum）、`routingPrompt`、`fallbackAgent`。
- `VesselConfig` 新增 `List<VesselAgentConfig> agents` 与 `AgentFlowConfig flow`。
- `VesselConfigBundle` 暴露 `getAgents()` / `getFlow()`。

- [x] **Step 7.1: 定义 `VesselAgentConfig` / `AgentFlowConfig` 与枚举**
- [x] **Step 7.2: 扩展 `VesselConfig` 与 `VesselConfigBundle`**
- [x] **Step 7.3: 更新 Vessel 配置模板与示例**
- [x] **Step 7.4: 更新 `VesselConfigLoaderTest` 覆盖多 Agent 配置读取**
- [x] **Step 7.5: 编译验证**
- [x] **Step 7.6: Commit**

### Task 8: 接入 SAA 多 Agent 模式

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SaaMultiAgentFactory.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/ReactAgentFactory.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngine.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/SaaMultiAgentFactoryTest.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngineMultiAgentTest.java`

**目标：** 当 Vessel 配置包含子 Agent 时，`SpringAiAlibabaAgentEngine` 调用对应的 SAA FlowAgent 而不是单个 ReactAgent。

SAA API（已确认）：
- `SequentialAgent.builder().name(...).description(...).subAgents(List<Agent>).build()`
- `ParallelAgent.builder().name(...).description(...).subAgents(List<Agent>).mergeStrategy(...).build()`
- `LlmRoutingAgent.builder().name(...).description(...).model(ChatModel).systemPrompt(...).instruction(...).fallbackAgent(...).subAgents(List<Agent>).build()`
- 所有 FlowAgent 都继承 `com.alibaba.cloud.ai.graph.agent.Agent`，支持 `invoke(List<Message>)` 返回 `Optional<OverAllState>`。

实现要点：
- `SaaMultiAgentFactory`：根据 `VesselConfigBundle` 的 `agents` / `flow` 构建子 Agent 列表与 FlowAgent。
- 子 Agent 优先使用 `ReactAgent`（复用现有 `ReactAgentFactory.buildSingleAgent(...)` 逻辑）。
- `ReactAgentFactory` 拆分 `build(TaskContext)` 为：
  - `buildSingleAgent(ctx)`：构建单 ReactAgent（现有逻辑）。
  - `buildMultiAgent(ctx)`：构建 FlowAgent（新逻辑）。
- `SpringAiAlibabaAgentEngine`：
  - `execute()` 判断是否有子 Agent；若有则调用 FlowAgent.invoke()，从最终 state 的 messages 提取 AssistantMessage 文本。
  - `executeStream()` 判断是否有子 Agent；若有则调用 FlowAgent.streamMessages()。
  - `resume()` 暂保持单 ReactAgent 路径；多 Agent 场景的 HITL 恢复可作为后续增强。

- [x] **Step 8.1: 创建 `SaaMultiAgentFactory`**
- [x] **Step 8.2: 重构 `ReactAgentFactory` 支持单 Agent / 多 Agent 构建**
- [x] **Step 8.3: 修改 `SpringAiAlibabaAgentEngine` 多 Agent 调用路径**
- [x] **Step 8.4: 创建多 Agent 单元测试（mock SAA FlowAgent）**
- [x] **Step 8.5: 编译验证与 `./init.sh` 全量验证**
- [x] **Step 8.6: Commit**

---

## Phase 6：Checkpoint Saver（可选）

### Task 9: 自定义 `VesselCheckpointSaver`

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/checkpoint/VesselCheckpointSaver.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/ReactAgentFactory.java`

**目标：** 把 SAA Graph 执行过程中的 checkpoint 持久化到 meta-claw 的记忆或文件系统，支持进程重启后恢复未完成的 Agent 任务。

SAA API（已确认）：
- `com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver` 抽象类。
- `ReactAgent.builder().compileConfig(CompileConfig.builder().checkpointSaver(saver).build())` 可配置 saver。

实现要点：
- 调研 `BaseCheckpointSaver` 的方法签名（get/checkpoint/update/delete）。
- `VesselCheckpointSaver` 以 `taskId` + `threadId` 为 key，把 checkpoint 序列化后写入 `.meta-claw/vessels/<vesselId>/checkpoints/`。
- 在 `ReactAgentFactory.buildSingleAgent(ctx)` 中为 ReactAgent 配置 saver；多 Agent 场景同样可配置。
- 新增集成测试：模拟一次被 HITL 中断的 Alibaba 引擎调用，持久化 checkpoint，然后从 checkpoint 恢复。

- [ ] **Step 9.1: 调研 `BaseCheckpointSaver` 接口**
- [ ] **Step 9.2: 实现 `VesselCheckpointSaver`**
- [ ] **Step 9.3: 在 `ReactAgentFactory` 中配置 saver**
- [ ] **Step 9.4: 新增 checkpoint 恢复测试**
- [ ] **Step 9.5: 编译验证与 `./init.sh` 全量验证**
- [ ] **Step 9.6: Commit**

---

## 可选深化：工具执行层进一步隔离

> 对应主设计文档第 9 章。优先级低于 Phase 3~4，建议在 Alibaba 引擎稳定后再评估。

### Task 10: 定义 `ExecutableTool` SPI

- [ ] 创建 `meta.claw.core.tool.ExecutableTool` 接口。

### Task 11: 创建 `SpringAiToolCallbackAdapter`

- [ ] 创建 `meta.claw.core.tool.adapter.SpringAiToolCallbackAdapter`。

### Task 12: 改造 `ToolSubSystem` 与 `AgentExecutor`

- [ ] `ToolSubSystem.getToolCallbacks()` 改为 `getExecutableTools()`。
- [ ] `AgentExecutor` 内部依赖 `ExecutableTool`，执行时通过 adapter 回包 `ToolCallback` 传给 `LlmClientManager`。
- [ ] 更新所有测试。
- [ ] 运行 `./init.sh` 验证。

---

## 资深用户当前实现不足点

> 以下问题不影响 `./init.sh` 通过，但会决定 `agent_engine: alibaba` 在生产环境中是否真正可用。后续 Task 排期应优先覆盖这些缺口。

| 优先级 | 不足点 | 关联 Task | 说明 |
|--------|--------|----------|------|
| P1 | 多 Agent 编排只有配置模型、没有执行能力 | Phase 5 Step 8 | `agents` / `flow` 配置已被解析，但 `SpringAiAlibabaAgentEngine` 仍走单 ReactAgent 路径。 |
| P1 | 缺少真实 LLM 端到端验证 | Phase 3~4 测试 | 当前核心测试以 Mockito 模拟 ReactAgent；真实模型的 metadata 格式（reasoning/usage/toolCalls）尚未验证。 |
| P2 | Checkpoint 持久化未开始 | Phase 6 | HITL 中断状态仍依赖内存；进程重启后 ticket 与图状态丢失。 |
| P2 | HITL 流式路径未完整覆盖 | Phase 4 测试 | `executeStream()` 触发 HITL 后的中断/恢复行为缺少明确测试与 CLI 验收。 |
| P2 | ReactAgentFactory 按请求构建 Agent，缺少缓存 | Phase 3 `ReactAgentFactory` | 避免 TaskContext 串用的同时引入了每次请求重新编译 Graph 的开销。 |
| P3 | 工具执行层未解耦 | 可选深化 Task 10~12 | `AgentExecutor` 仍直接依赖 Spring AI `ToolCallback`，限制非 Spring AI 工具的接入。 |
| P3 | Metrics token usage 依赖 SAA metadata | Phase 3 Task 3 | `MetaClawModelMetricsHook` 读取 `AssistantMessage.metadata` 中的 `usage`，真实 LLM 下是否稳定未知。 |
| P3 | 编排配置类型安全不足 | Phase 5 Step 7 | `AgentFlowConfig.mode` 以 `String` 存储，拼写错误要到执行阶段才暴露。 |
| P3 | CLI/Gateway 层 engine 切换验收不足 | Phase 3~4 验证 | 当前验证集中在 core 模块，真实 CLI 聊天路径未覆盖 alibaba 引擎。 |

---

## Self-Review Checklist

实施前由执行者自检：

- [ ] **Spec coverage:** Phase 3~6 与可选工具抽象均已在主设计文档中找到对应章节。
- [ ] **SAA API 已确认:** `streamMessages`、`AgentHook`、`ModelHook`、`HookPosition` 的类名/方法名与实际 JAR 一致。
- [ ] **Type consistency:** `SpiStreamingCallback` 的调用方式与 native 引擎的 `StreamingAgentExecutor` 一致。
- [ ] **Placeholder scan:** 计划中没有 TBD/TODO/"implement later"；所有代码片段、命令、期望输出均已给出。
- [ ] **File paths:** 所有路径与当前仓库结构一致。
- [ ] **Validation gate:** 每个 Task 都有明确的编译/测试命令与期望输出；最终 `./init.sh` 为全量验证。

---

## 执行入口

建议按 Task 1→5 顺序执行 Phase 3；Phase 4/5/6 与可选任务按需后续推进。每个 Task 的 Step 1 为实际代码改动，后续为验证与提交。如果 `./init.sh` 在沙箱内失败（Mockito/ByteBuddy 自附加限制），请在允许 JVM agent 附加的真实环境中重新运行。
