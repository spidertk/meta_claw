# Agent Engine SPI 后续实施计划（Phase 2 ~ Phase 6）

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在主设计文档 `2026-06-15-agent-execution-abstraction-design.md` 已确定的 `AgentEngine` SPI 基础上，继续实现 `SpringAiAlibabaAgentEngine`，让 `agent_engine: alibaba` 的 Vessel 能真正跑通同步/流式对话、工具调用、HITL、Metrics，并最终支持多 Agent 编排与 checkpoint 持久化。

**Current baseline:** Phase 0+1 已完成且 `./init.sh` 通过（2026-06-16）。`AgentEngine` SPI、`AgentEngineFactory`、`NativeAgentEngine`、`VesselConfig.agentEngine` 与 `AlibabaAgentConfig` 已落地，`VesselRuntime` 已通过工厂按配置路由。

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring AI 1.1.8, Spring AI Alibaba 1.1.2.3, Maven, JUnit 5, Mockito, Lombok.

---

## 文件结构总览（新增/修改）

| 文件 | 操作 | 职责 |
|------|------|------|
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpiMessageConverter.java` | 创建 | `SpiMessage` ↔ Spring AI `Message` 双向转换 |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/ReactAgentFactory.java` | 创建 | 根据 `VesselProfile` 构造 SAA `ReactAgent` |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngine.java` | 创建 | Alibaba 引擎实现 |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/alibabahook/MetaClawMetricsHook.java` | 创建 | Phase 3 Metrics Hook |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/alibabahook/MetaClawHitlHook.java` | 创建 | Phase 4 HITL Hook |
| `meta-claw-core/src/main/java/meta/claw/core/llm/provider/LlmClientProvider.java` | 修改 | 新增 `createChatModel(ProviderConfig)` 默认方法 |
| `meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiLlmClientProvider.java` | 修改 | 实现 `createChatModel` |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java` | 修改 | 流式 callback 持久化适配（若 SPI 改为 void） |
| `.meta-claw/vessels/default/config.yaml` | 修改 | 补充 `agent_engine` / `alibaba_agent` 示例 |
| `init.sh` | 修改 | 将新增 P0 测试纳入基线 |
| `feature_list.json` | 修改 | 更新 `agent-engine-001` 证据 |
| `claude-progress.md` | 修改 | 记录 Phase 2+ 进度 |
| `docs/superpowers/specs/2026-06-15-agent-execution-abstraction-design.md` | 修改 | 维护已实现/未实现进度 |

---

## Phase 2：Alibaba 同步引擎跑通

### Task 1: 实现 `SpiMessageConverter`

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpiMessageConverter.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/SpiMessageConverterTest.java`

**目标：** 在 `meta.claw.core.llm.SpiMessage` 与 Spring AI `org.springframework.ai.chat.messages.Message` 之间做双向转换。这是接入 `ReactAgent` 的最关键适配层，必须正确处理 tool 消息的 `toolCallId`。

- [ ] **Step 1.1: 创建转换器**

  ```java
  package meta.claw.core.runtime.engine;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import meta.claw.core.llm.SpiMessage;
  import meta.claw.core.tool.SpiToolCall;
  import org.springframework.ai.chat.messages.*;

  import java.util.ArrayList;
  import java.util.List;
  import java.util.Map;
  import java.util.stream.Collectors;

  public final class SpiMessageConverter {

      private static final ObjectMapper MAPPER = new ObjectMapper();

      private SpiMessageConverter() {}

      public static List<Message> toSpringMessages(List<SpiMessage> messages) {
          List<Message> result = new ArrayList<>(messages.size());
          for (SpiMessage m : messages) {
              result.add(toSpringMessage(m));
          }
          return result;
      }

      public static Message toSpringMessage(SpiMessage m) {
          String role = m.getRole() != null ? m.getRole().toLowerCase() : "";
          return switch (role) {
              case "system" -> new SystemMessage(m.getContent());
              case "user" -> new UserMessage(m.getContent());
              case "assistant" -> new AssistantMessage(
                      m.getContent() != null ? m.getContent() : "",
                      Map.of(),
                      toSpringToolCalls(m.getToolCalls())
              );
              case "tool" -> {
                  ToolResult tr = parseToolResultJson(m.getContent());
                  yield new ToolResponseMessage(
                          List.of(new ToolResponseMessage.ToolResponse(
                                  tr.toolCallId(), tr.toolName(), tr.result())),
                          Map.of()
                  );
              }
              default -> new UserMessage(m.getContent());
          };
      }

      private static List<AssistantMessage.ToolCall> toSpringToolCalls(List<SpiToolCall> toolCalls) {
          if (toolCalls == null || toolCalls.isEmpty()) return List.of();
          return toolCalls.stream()
                  .map(tc -> new AssistantMessage.ToolCall(
                          tc.getId(),
                          tc.getType() != null ? tc.getType() : "function",
                          tc.getName(),
                          tc.getArgumentsJson() != null ? tc.getArgumentsJson() : "{}"))
                  .collect(Collectors.toList());
      }

      private record ToolResult(String toolCallId, String toolName, String result) {}

      private static ToolResult parseToolResultJson(String json) {
          try {
              Map<?, ?> map = MAPPER.readValue(json, Map.class);
              return new ToolResult(
                      String.valueOf(map.get("toolCallId")),
                      String.valueOf(map.get("toolName")),
                      String.valueOf(map.get("result")));
          } catch (Exception e) {
              return new ToolResult("unknown", "unknown", json);
          }
      }
  }
  ```

  > 说明：当前 `AgentExecutor.buildToolResultJson()` 生成的 JSON 字段为 `toolCallId`/`toolName`/`result`，与 `parseToolResultJson` 约定一致。

- [ ] **Step 1.2: 创建单元测试**

  覆盖：
  - system / user / assistant 三种消息转换
  - assistant 带 `toolCalls` 时，`AssistantMessage.ToolCall` 字段正确
  - tool 消息能从 JSON 解析出 `toolCallId`/`toolName`/`result`
  - 未知 role 回退到 `UserMessage`

- [ ] **Step 1.3: 编译验证**

  ```bash
  mvn clean compile -pl meta-claw-core -am -q
  ```

  Expected: `BUILD SUCCESS`。

- [ ] **Step 1.4: Commit**

  ```bash
  git add meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpiMessageConverter.java \
          meta-claw-core/src/test/java/meta/claw/core/runtime/engine/SpiMessageConverterTest.java
  git commit -m "feat(engine): add SpiMessageConverter for Spring AI Alibaba integration"
  ```

---

### Task 2: 扩展 LLM Provider 支持 `createChatModel`

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/llm/provider/LlmClientProvider.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiLlmClientProvider.java`

SAA `ReactAgent` 直接依赖 `ChatModel` 而不是 `ChatClient`，因此需要在 provider 接口上暴露 `ChatModel`。

- [ ] **Step 2.1: 在 `LlmClientProvider` 新增默认方法**

  ```java
  /**
   * 创建 ChatModel，供 Spring AI Alibaba ReactAgent 等直接消费。
   * 默认实现从 {@link #createRaw(ProviderConfig)} 解包 ChatModel。
   */
  default ChatModel createChatModel(ProviderConfig providerConfig) {
      ChatClient chatClient = createRaw(providerConfig);
      // ChatClient 标准实现内部持有 ChatModel，但无公开 getter；
      // 因此各 provider 应覆写本方法直接构造 ChatModel。
      throw new UnsupportedOperationException(
              "Provider " + providerName() + " must implement createChatModel directly");
  }
  ```

- [ ] **Step 2.2: 在 `OpenAiLlmClientProvider` 覆写 `createChatModel`**

  ```java
  @Override
  public ChatModel createChatModel(ProviderConfig providerConfig) {
      return buildChatModel(providerConfig);
  }
  ```

  注意 `buildChatModel` 当前为 `private`，需要改为 `public` 或包可见，供本方法调用。

- [ ] **Step 2.3: 编译验证**

  ```bash
  mvn clean compile -pl meta-claw-core -am -q
  ```

- [ ] **Step 2.4: Commit**

  ```bash
  git add meta-claw-core/src/main/java/meta/claw/core/llm/provider/LlmClientProvider.java \
          meta-claw-core/src/main/java/meta/claw/core/llm/provider/OpenAiLlmClientProvider.java
  git commit -m "feat(llm): expose createChatModel on LlmClientProvider for SAA ReactAgent"
  ```

---

### Task 3: 实现 `ReactAgentFactory`

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/ReactAgentFactory.java`

- [ ] **Step 3.1: 创建工厂**

  ```java
  package meta.claw.core.runtime.engine;

  import com.alibaba.cloud.ai.graph.agent.ReactAgent;
  import meta.claw.core.config.ProviderConfig;
  import meta.claw.core.llm.provider.LlmClientProviderManager;
  import meta.claw.core.runtime.TaskContext;
  import meta.claw.core.runtime.subsystem.ToolSubSystem;
  import org.springframework.ai.chat.model.ChatModel;
  import org.springframework.ai.tool.ToolCallback;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.stereotype.Component;

  import java.util.List;
  import java.util.Map;
  import java.util.concurrent.ConcurrentHashMap;

  /**
   * 根据 {@link TaskContext} 构造 SAA {@link ReactAgent} 实例。
   *
   * <p>每个 Vessel 缓存一个 ReactAgent；工具/HITL/模型配置变化后可通过 {@link #invalidate(String)} 重建。</p>
   */
  @Component
  public class ReactAgentFactory {

      @Autowired
      private LlmClientProviderManager llmClientProviderManager;

      private final Map<String, ReactAgent> cache = new ConcurrentHashMap<>();

      public ReactAgent get(TaskContext ctx) {
          String vesselId = ctx.getTask().getVesselId();
          return cache.computeIfAbsent(vesselId, id -> build(ctx));
      }

      private ReactAgent build(TaskContext ctx) {
          var bundle = ctx.getProfile().getBundle();
          ProviderConfig providerConfig = bundle.getProviderConfig();
          ChatModel chatModel = llmClientProviderManager.create(providerConfig).createChatModel(providerConfig);

          ToolSubSystem toolSubSystem = ctx.getSubSystem("tool");
          List<ToolCallback> toolCallbacks = toolSubSystem != null ? toolSubSystem.getToolCallbacks() : List.of();

          return ReactAgent.builder()
                  .name(bundle.getVesselName())
                  .description(bundle.getVesselDescription())
                  .model(chatModel)
                  .systemPrompt("") // system prompt 已由 VesselRuntime 组装进 messages
                  .tools(toolCallbacks.toArray(new ToolCallback[0]))
                  .build();
      }

      public void invalidate(String vesselId) {
          cache.remove(vesselId);
      }
  }
  ```

  > 注意：若 `LlmClientProviderManager.create(...)` 返回的是 `ChatClient`，而 `ChatClient` 没有 `createChatModel` 方法，则需要调整：让 `ReactAgentFactory` 直接注入 `LlmClientProviderManager`，调用其 `createChatModel` 方法。当前 `LlmClientProviderManager` 没有 `createChatModel`，需要补充。

- [ ] **Step 3.2: 在 `LlmClientProviderManager` 增加 `createChatModel` 路由**

  ```java
  public ChatModel createChatModel(ProviderConfig providerConfig) {
      LlmClientProvider provider = resolveProvider(providerConfig);
      return provider.createChatModel(providerConfig);
  }
  ```

  可复用现有 `resolveProvider` 逻辑（把 create/createRaw 中的重复查找提取为私有方法）。

- [ ] **Step 3.3: 编译验证**

  ```bash
  mvn clean compile -pl meta-claw-core -am -q
  ```

- [ ] **Step 3.4: Commit**

  ```bash
  git add meta-claw-core/src/main/java/meta/claw/core/runtime/engine/ReactAgentFactory.java \
          meta-claw-core/src/main/java/meta/claw/core/llm/provider/LlmClientProviderManager.java
  git commit -m "feat(engine): add ReactAgentFactory and ChatModel routing"
  ```

---

### Task 4: 实现 `SpringAiAlibabaAgentEngine`

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngine.java`
- Create: `meta-claw-core/src/test/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngineTest.java`

- [ ] **Step 4.1: 创建实现类**

  ```java
  package meta.claw.core.runtime.engine;

  import com.alibaba.cloud.ai.graph.agent.ReactAgent;
  import meta.claw.core.llm.SpiChatRequest;
  import meta.claw.core.llm.SpiStreamingCallback;
  import meta.claw.core.message.Reply;
  import meta.claw.core.message.ReplyType;
  import meta.claw.core.runtime.TaskContext;
  import meta.claw.core.runtime.hitl.ApprovalResolution;
  import meta.claw.core.runtime.hitl.ApprovalTicket;
  import org.springframework.ai.chat.messages.AssistantMessage;
  import org.springframework.ai.chat.messages.Message;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.stereotype.Component;

  import java.util.List;

  /**
   * 基于 Spring AI Alibaba {@link ReactAgent} 的执行引擎实现。
   *
   * <p>Phase 2 先实现同步 call；Phase 3 接入 streamMessages；Phase 4 接入 HITL Hook。</p>
   */
  @Component
  public class SpringAiAlibabaAgentEngine implements AgentEngine {

      @Autowired
      private ReactAgentFactory reactAgentFactory;

      @Override
      public Reply execute(TaskContext ctx, SpiChatRequest request) {
          ReactAgent agent = reactAgentFactory.get(ctx);
          List<Message> messages = SpiMessageConverter.toSpringMessages(request.getMessages());
          try {
              AssistantMessage result = agent.call(messages);
              return new Reply(ReplyType.TEXT, result.getText());
          } catch (com.alibaba.cloud.ai.graph.exception.GraphRunnerException e) {
              throw new RuntimeException("Alibaba agent execution failed: " + e.getMessage(), e);
          }
      }

      @Override
      public Reply executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback) {
          // Phase 3：接入 SAA streamMessages
          throw new UnsupportedOperationException(
                  "SpringAiAlibabaAgentEngine streaming is planned for Phase 3");
      }

      @Override
      public Reply resume(TaskContext ctx, SpiChatRequest request,
                          ApprovalTicket ticket, ApprovalResolution resolution) {
          // Phase 4：把 ApprovalResolution 结果重新注入 messages 再 call
          ReactAgent agent = reactAgentFactory.get(ctx);
          List<Message> messages = SpiMessageConverter.toSpringMessages(request.getMessages());
          try {
              AssistantMessage result = agent.call(messages);
              return new Reply(ReplyType.TEXT, result.getText());
          } catch (com.alibaba.cloud.ai.graph.exception.GraphRunnerException e) {
              throw new RuntimeException("Alibaba agent resume failed: " + e.getMessage(), e);
          }
      }

      @Override
      public String name() {
          return "alibaba";
      }
  }
  ```

- [ ] **Step 4.2: 创建单元测试**

  使用 Mockito 模拟 `ReactAgentFactory` 与 `ReactAgent`：
  - `name()` 返回 `alibaba`
  - `execute()` 委托给 `ReactAgent.call()` 并返回文本回复
  - `executeStream()` 抛 `UnsupportedOperationException`

- [ ] **Step 4.3: 编译验证**

  ```bash
  mvn clean compile -pl meta-claw-core -am -q
  ```

- [ ] **Step 4.4: Commit**

  ```bash
  git add meta-claw-core/src/main/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngine.java \
          meta-claw-core/src/test/java/meta/claw/core/runtime/engine/SpringAiAlibabaAgentEngineTest.java
  git commit -m "feat(engine): implement SpringAiAlibabaAgentEngine (sync call)"
  ```

---

### Task 5: 更新 Vessel 配置示例与文档

**Files:**
- Modify: `meta-claw-core/src/main/resources/templates/user/vessel.meta.tmpl.yaml`

- [ ] **Step 5.1: 在 Vessel 配置模板中补充 engine 示例**

  在 YAML 末尾新增（默认仍保持 `native`）：

  ```yaml
  # Agent 执行引擎：native（自研 ReAct）或 alibaba（Spring AI Alibaba ReactAgent）
  agent_engine: native
  alibaba_agent:
    parallel_tool_execution: true
    max_parallel_tools: 5
    return_reasoning_contents: true
  ```

  确认 `VesselConfigLoader` 的 SnakeYAML 驼峰映射能正确读取 `agent_engine` → `agentEngine` 与 `alibaba_agent` → `alibabaAgent`。

- [ ] **Step 5.2: Commit**

  ```bash
  git add meta-claw-core/src/main/resources/templates/user/vessel.meta.tmpl.yaml
  git commit -m "chore(config): add agent_engine / alibaba_agent example to vessel template"
  ```

---

### Task 6: 把新增测试纳入 P0 基线并全量验证

**Files:**
- Modify: `init.sh`

- [ ] **Step 6.1: 在 `init.sh` 的 `-Dtest=` 列表追加 `SpiMessageConverterTest,SpringAiAlibabaAgentEngineTest`**

  第 13 行与第 51 行两处同步更新。

- [ ] **Step 6.2: 运行 `./init.sh` 全量验证**

  ```bash
  ./init.sh
  ```

  Expected: 全仓编译 SUCCESS，P0 测试全部通过（含新增 engine 测试）。

- [ ] **Step 6.3: Commit**

  ```bash
  git add init.sh
  git commit -m "chore(init): include Phase 2 engine tests in P0 baseline"
  ```

---

## Phase 3：流式 + Metrics Hook

### Task 7: 接入 SAA 流式输出

- [ ] 调研 `ReactAgent.streamMessages(List<Message>)` 返回类型（`Flux<Message>` 或 `Flux<NodeOutput>`）。
- [ ] 修改 `SpringAiAlibabaAgentEngine.executeStream()`，将流式事件透传到 `SpiStreamingCallback`。
- [ ] 在 `VesselRuntime.chatStream()` 中处理流式结果落盘；若 `AgentEngine.executeStream` 最终保持 `Reply` 返回类型，则保持现状。
- [ ] 新增 `SpringAiAlibabaAgentEngineStreamTest`。
- [ ] 运行 `./init.sh` 验证。

### Task 8: 实现 `MetaClawMetricsHook`

- [ ] 在 `meta-claw-core/src/main/java/meta/claw/core/runtime/engine/alibabahook/` 创建 `MetaClawMetricsHook`。
- [ ] 实现 `AgentHook`/`ModelHook` 记录任务开始/结束、LLM latency、token usage、tool call。
- [ ] 在 `ReactAgentFactory.build()` 中注册 hook。
- [ ] 新增 `MetaClawMetricsHookTest`。
- [ ] 运行 `./init.sh` 验证。

---

## Phase 4：HITL Hook

### Task 9: 实现 `MetaClawHitlHook`

- [ ] 创建 `MetaClawHitlHook implements ModelHook`，位置 `AFTER_MODEL`。
- [ ] 从模型响应中提取 `AssistantMessage.ToolCall`，转成 `SpiToolCall`。
- [ ] 调用 `HitlSubSystem.evaluate(...)`；若有挂起则抛 `HitlSuspendedException`。
- [ ] 在 `ReactAgentFactory` 注册 hook。
- [ ] 实现 `SpringAiAlibabaAgentEngine.resume()`：把 `ApprovalResolution` 中 APPROVED/REJECTED 的 tool 结果注入 messages 后重新 `call()`。
- [ ] 新增 `MetaClawHitlHookTest` 与 `SpringAiAlibabaAgentEngineResumeTest`。
- [ ] 运行 `./init.sh` 验证。

---

## Phase 5：多 Agent 编排

### Task 10: VesselProfile 支持子 Agent 配置

- [ ] 在 `VesselConfig` 中新增 `agents` 字段（子 Agent 列表 + 路由策略）。
- [ ] 设计 YAML 结构：`agents` / `routing` / `parallel` / `sequential`。
- [ ] 更新 `VesselConfigLoader` 与 `VesselConfigBundle`。

### Task 11: 接入 SAA 多 Agent 模式

- [ ] 调研 `SequentialAgent`、`LlmRoutingAgent`、`Supervisor` 的 API。
- [ ] 在 `ReactAgentFactory` 中支持构建子 Agent 列表。
- [ ] 实现 `SpringAiAlibabaAgentEngine` 对多 Agent 路由的调用。
- [ ] 新增集成测试。
- [ ] 运行 `./init.sh` 验证。

---

## Phase 6：Checkpoint Saver（可选）

### Task 12: 自定义 `VesselCheckpointSaver`

- [ ] 调研 SAA `BaseCheckpointSaver` 接口。
- [ ] 实现 `VesselCheckpointSaver`，把 SAA thread 状态持久化到 `MemorySubSystem` 或独立文件。
- [ ] 在 `ReactAgentFactory` 中配置 saver。
- [ ] 新增进程重启恢复测试。
- [ ] 运行 `./init.sh` 验证。

---

## 可选深化：工具执行层进一步隔离

> 对应主设计文档第 9 章。优先级低于 Phase 2~4，建议在 Alibaba 引擎稳定后再评估。

### Task 13: 定义 `ExecutableTool` SPI

- [ ] 创建 `meta.claw.core.tool.ExecutableTool` 接口。

### Task 14: 创建 `SpringAiToolCallbackAdapter`

- [ ] 创建 `meta.claw.core.tool.adapter.SpringAiToolCallbackAdapter`。

### Task 15: 改造 `ToolSubSystem` 与 `AgentExecutor`

- [ ] `ToolSubSystem.getToolCallbacks()` 改为 `getExecutableTools()`。
- [ ] `AgentExecutor` 内部依赖 `ExecutableTool`，执行时通过 adapter 回包 `ToolCallback` 传给 `LlmClientManager`。
- [ ] 更新所有测试。
- [ ] 运行 `./init.sh` 验证。

---

## Self-Review Checklist

实施前由执行者自检：

- [ ] **Spec coverage:** Phase 2~6 与可选工具抽象均已在主设计文档中找到对应章节。
- [ ] **Type consistency:** `SpiMessageConverter` 对四种 role 的转换与 `AgentExecutor` 生成的 tool result JSON 约定一致。
- [ ] **Placeholder scan:** 计划中没有 TBD/TODO/"implement later"；所有代码片段、命令、期望输出均已给出。
- [ ] **File paths:** 所有路径与当前仓库结构一致。
- [ ] **Validation gate:** 每个 Task 都有明确的编译/测试命令与期望输出；最终 `./init.sh` 为全量验证。

---

## 执行入口

建议按 Task 1→6 顺序执行 Phase 2；Phase 3/4/5/6 与可选任务按需后续推进。每个 Task 的 Step 1 为实际代码改动，后续为验证与提交。如果 `./init.sh` 在沙箱内失败（Mockito/ByteBuddy 自附加限制），请在允许 JVM agent 附加的真实环境中重新运行。
