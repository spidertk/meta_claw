# 解除 `LlmClientManager ↔ ToolRegistry` Spring 循环依赖设计

> 日期：2026-07-02  
> 范围：`meta-claw-core` 运行时层、`meta-claw-tool` 工具生态  
> 目标：在不破坏现有功能的前提下，切断启动阶段的 Spring Bean 循环依赖，使 `./init.sh` 与 `meta-claw-bootstrap` 能正常启动。

---

## 1. 问题描述

当前启动 `meta-claw-cli` 或 `meta-claw-bootstrap` 时，Spring 报错存在 Bean 循环依赖：

```
cliApplication
  ↓
vesselManager
  ↓
vesselRuntime
  ↓
agentEngineFactory
  ↓
nativeAgentEngine
  ↓
agentExecutor
  ↓
llmClientManager
  ↓
toolRegistry
  ↓
knowledgeTool
  ↓
knowledgeManager
  ↓
knowledgeAnalyzer
┌─────┐
↑     ↓
|  llmClientManager
└─────┘
```

### 1.1 循环的精确依赖边

| 起点 | 依赖 | 终点 | 说明 |
|------|------|------|------|
| `CliApplication` | `@Autowired` | `VesselManager` | CLI 入口 |
| `VesselManager` | 创建 | `VesselRuntime` | 每个 vessel 一个 Runtime |
| `VesselRuntime` | `@Autowired` | `AgentEngineFactory` | 按配置选择引擎 |
| `AgentEngineFactory` | `@Autowired` | `NativeAgentEngine` | `agent_engine: native` |
| `NativeAgentEngine` | `@Autowired` | `AgentExecutor` | 同步 ReAct 循环 |
| `AgentExecutor` | `@Autowired` | `LlmClientManager` | LLM 调用 |
| `LlmClientManager` | `@Autowired` | `ToolRegistry` | **问题边：LLM 层依赖工具层** |
| `ToolRegistry` | 扫描 `@ToolService` | `KnowledgeTool` | 工具注册 |
| `KnowledgeTool` | 构造注入 | `KnowledgeManager` | 知识采集/检索 |
| `KnowledgeManager` | 构造注入 | `KnowledgeAnalyzer` | LLM 分析 |
| `KnowledgeAnalyzer` | 构造注入 | `SpiLlmClient` | 实际实现为 `LlmClientManager` |

### 1.2 关键观察

- `KnowledgeAnalyzer` 与 `VisionDescriber` 调用的是 `SpiLlmClient.chat(SpiChatRequest)`，做的是**纯文本/多模态分析**，不需要工具调用。
- `AgentExecutor` / `StreamingAgentExecutor` 已经通过 `chatWithTools(..., ToolCallback[])` / `streamWithTools(..., ToolCallback[])` 传入工具回调，不需要 `LlmClientManager` 自己再去 `ToolRegistry` 拉取。
- `LlmClientManager.chatStream(...)` 在当前主链路中**没有实际调用方**（`VesselRuntime.chatStream` 已走 `AgentEngine.executeStream` → `StreamingAgentExecutor` → `streamWithTools`）。

因此，循环的根因是 **`LlmClientManager` 承担了本不属于它的职责**：它作为底层 LLM 客户端，却反向依赖了上层的 `ToolRegistry`。

---

## 2. 设计目标

1. **彻底切断启动期循环依赖**，不再依赖 `@Lazy` / `ObjectProvider` 等延迟解析技巧。
2. **保持现有行为不变**：
   - Agent 的 ReAct 工具调用循环继续正常工作；
   - 知识分析、图片描述等纯 LLM 调用继续正常工作；
   - 流式输出、HITL、Metrics 等周边能力不受影响。
3. **改善模块分层**：`LlmClientManager` 只负责“把 `SpiChatRequest` 发给 LLM 并返回 `SpiChatResponse`”，工具装配由调用方（`AgentExecutor` / `ToolSubSystem`）负责。
4. **不强制迁移 knowledge 包位置**：本方案只改依赖方向，不改动 `knowledge` 包所在模块（当前在 `meta-claw-core` 或迁回 `meta-claw-tool` 均可独立决策）。

---

## 3. 推荐方案：全 Advisor 化（已确认）

基于进一步讨论，最终采用**全 Advisor 化**方案：

- 工具注入、响应提取（content / reasoningContent / usage / toolCalls）、Metrics 记录全部下沉到 Spring AI Advisor；
- `OpenAiLlmClientProvider.buildChatClient()` 负责把 Advisors 注册到 `ChatClient`；
- `LlmClientManager.chat()` 只负责消息类型转换、触发调用、从共享上下文中读取结果并组装 `SpiChatResponse`；
- `LlmClientManager` 不再依赖 `ToolRegistry`。

### 3.1 为什么这样能破环

`OpenAiLlmClientProvider` 虽然要在 `buildChatClient()` 里构造依赖 `ToolRegistry` 的 `ToolRegistryAdvisor`，但它**不通过字段静态注入**这个 Advisor，而是在运行时通过 `applicationContext.getBean(ToolRegistry.class)` 获取并 `new ToolRegistryAdvisor(...)`。因此静态 Bean 依赖图变成：

```
LlmClientManager → LlmClientProviderManager → OpenAiLlmClientProvider
                                                        │
                                                        │ (运行时 new Advisor)
                                                        ▼
                                                ToolRegistry → KnowledgeTool → ...
                                                                    ▲
                                                            KnowledgeAnalyzer
                                                                    │
                                                            SpiLlmClient (LlmClientManager)
```

`KnowledgeAnalyzer → LlmClientManager` 这条边在启动期不再触发 `LlmClientManager` 提前创建，因为 `LlmClientManager` 不再静态依赖 `ToolRegistry`。Spring 可以按 `ToolRegistry → KnowledgeTool → KnowledgeManager → KnowledgeAnalyzer → LlmClientManager` 的顺序完成创建，环被切断。

### 3.2 Advisor 职责划分

| Advisor | 类型 | 职责 | 依赖 |
|---------|------|------|------|
| `ToolRegistryAdvisor` | `CallAdvisor` + `StreamAdvisor` | 从 `ToolRegistry` 拉取所有工具，构造 `ToolCallingChatOptions` 并注入请求 | `ToolRegistry`（运行时获取） |
| `MetaClawResponseCallAdvisor` | `CallAdvisor` | 包裹实际 LLM 调用，测量 latency；从 `ChatResponse` 提取 reasoningContent、usage、toolCalls；记录 Metrics；把结果写入 `MetaClawCallContext` | `MetricsRecorder`（可选）、`ObjectMapper` |
| `MetaClawResponseStreamAdvisor`（可选） | `StreamAdvisor` | 流式场景下累积 content / reasoning / toolCalls，提取 usage，结束时记录 Metrics | `MetricsRecorder`（可选）、`ObjectMapper` |

> 注：本次先实施同步路径 `chat()` 的 Advisor 化；`chatStream()` 改为委托给已有的 `streamWithTools(..., ToolCallback[], callback)`，从而复用现有流式工具/回调逻辑并移除 `ToolRegistry` 依赖。

### 3.3 共享上下文 `MetaClawCallContext`

`LlmClientManager.chat()` 在发起调用前创建一个可变的 `MetaClawCallContext`，通过 `.advisors(spec -> spec.param("metaClawCallContext", ctx))` 传入 Advisor 链。Advisors 在调用过程中把提取结果写回同一个对象引用，`LlmClientManager` 调用结束后直接读取。

```java
@Data
public class MetaClawCallContext {
    private String vesselId;
    private String sessionId;
    private long startTime;
    private String content;
    private String reasoningContent;
    private SpiUsage usage;
    private List<SpiToolCall> toolCalls;
}
```

### 3.4 `OpenAiLlmClientProvider.buildChatClient()` 改造

```java
private ChatClient buildChatClient(ProviderConfig providerConfig) {
    ChatModel chatModel = buildChatModel(providerConfig);
    return ChatClient.builder(chatModel)
            .defaultAdvisors(
                    ToolCallAdvisor.builder().build(),                    // 外层：Spring AI 工具调用循环
                    shortMemoryAdvisor,                                    // 流式记忆持久化
                    new ToolRegistryAdvisor(applicationContext.getBean(ToolRegistry.class)),
                    new MetaClawResponseCallAdvisor(
                            applicationContext.getBeanProvider(MetricsRecorder.class).getIfAvailable(),
                            objectMapper)
            )
            .build();
}
```

Advisors 顺序：
- `ToolCallAdvisor` 默认 order 最外层；
- `ToolRegistryAdvisor` order=0，位于 `ToolCallAdvisor` 内侧，确保它注入的 `ToolCallingChatOptions` 能被 `ToolCallAdvisor` 看到；
- `MetaClawResponseCallAdvisor` order=100，位于最内侧，紧挨实际模型调用，用于精确测量 latency 和提取响应。

### 3.5 `LlmClientManager.chat()` 简化后

```java
@Override
public SpiChatResponse chat(SpiChatRequest request) {
    List<Message> messages = request.getMessages().stream()
            .map(this::toSpringMessage)
            .collect(Collectors.toCollection(ArrayList::new));

    MetaClawCallContext ctx = new MetaClawCallContext();
    ctx.setVesselId(request.getVesselId());
    ctx.setSessionId(request.getSessionId());

    buildChatClient(request.getVesselId())
            .prompt(new Prompt(messages))
            .advisors(spec -> spec.param("metaClawCallContext", ctx))
            .call()
            .chatResponse();

    return SpiChatResponse.builder()
            .content(ctx.getContent() != null ? ctx.getContent() : "")
            .reasoningContent(ctx.getReasoningContent())
            .toolCalls(ctx.getToolCalls() != null ? ctx.getToolCalls() : List.of())
            .usage(ctx.getUsage())
            .build();
}
```

### 3.6 `LlmClientManager.chatStream()` 处理

`chatStream()` 当前在主链路无调用方，且其流式处理逻辑与 `streamWithTools()` 高度重复。改为：

```java
@Override
public void chatStream(SpiChatRequest request, SpiStreamingCallback callback) {
    streamWithTools(request, new ToolCallback[0], callback);
}
```

这样：
- 不引入新的 `ToolRegistry` 依赖；
- 复用经过多轮 bug 修复的 `streamWithTools()` 累积/解析逻辑；
- 行为等价于“无工具的流式调用”。

---

## 4. 方案架构图

### 4.1 改前（存在循环）

### 3.1 核心改动

#### 3.1.1 `LlmClientManager` 移除 `ToolRegistry` 依赖

```java
@Component
public class LlmClientManager implements SpiLlmClient {
    @Autowired
    private LlmClientProviderManager llmClientProviderManager;
    @Autowired
    private RuntimeConfigResolver runtimeConfigResolver;
    @Autowired(required = false)
    private MetricsRecorder metricsRecorder;

    // 删除：
    // @Autowired
    // private ToolRegistry toolRegistry;
}
```

#### 3.1.2 `chat()` 改为纯 Prompt 调用

当前 `chat()` 内部调用 `toolRegistry.getToolInstances()` 并构造 `ToolCallingChatOptions`。改为直接传 `new Prompt(messages)`，不携带任何工具。

```java
@Override
public SpiChatResponse chat(SpiChatRequest request) {
    List<Message> messages = request.getMessages().stream()
            .map(this::toSpringMessage)
            .collect(Collectors.toCollection(ArrayList::new));

    long startTime = System.currentTimeMillis();
    ChatResponse chatResponse = buildChatClient(request.getVesselId())
            .prompt(new Prompt(messages))   // 纯 Prompt，不带工具
            .call()
            .chatResponse();
    long latency = System.currentTimeMillis() - startTime;

    Generation gen = chatResponse.getResult();
    String content = gen != null && gen.getOutput() != null ? gen.getOutput().getText() : "";
    String reasoningContent = extractReasoningContent(gen);
    SpiUsage usage = extractUsage(chatResponse);
    List<SpiToolCall> toolCalls = extractToolCalls(gen);

    recordLlmMetrics(request.getVesselId(), latency, usage);

    return SpiChatResponse.builder()
            .content(content != null ? content : "")
            .reasoningContent(reasoningContent)
            .toolCalls(toolCalls)
            .usage(usage)
            .build();
}
```

> 说明：`chat()` 的返回对象仍保留 `toolCalls` 字段，因为 Spring AI 的 `AssistantMessage` 仍可能携带 tool_calls（例如用户通过 prompt 诱导模型输出 function call 格式）。只是 Spring AI 不再主动提供可用工具列表。

#### 3.1.3 `chatStream()` 改为纯 Prompt 流式调用

```java
@Override
public void chatStream(SpiChatRequest request, SpiStreamingCallback callback) {
    List<Message> messages = request.getMessages().stream()
            .map(this::toSpringMessage)
            .collect(Collectors.toCollection(ArrayList::new));

    buildChatClient(request.getVesselId())
            .prompt(new Prompt(messages))   // 纯 Prompt，不带工具
            .stream()
            .chatResponse()
            // ... 原有 callback 处理逻辑保持不变
            .subscribe();
}
```

> 说明：`chatStream()` 当前在主链路中无调用方，但保留为 `SpiLlmClient` 接口实现。若未来有调用方需要工具，应使用 `streamWithTools(..., ToolCallback[])`。

#### 3.1.4 `chatWithTools()` / `streamWithTools()` 保持不变

这两个方法已经接收 `ToolCallback... toolCallbacks`，调用方自行决定传什么工具。无需改动。

### 3.2 调用方侧无需改动

- `AgentExecutor.reactLoop(...)` 已调用 `llmClient.chatWithTools(request, tools.toArray(...))`。
- `StreamingAgentExecutor.execute(...)` 已调用 `llmClient.streamWithTools(request, tools.toArray(...), callback)`。
- `KnowledgeAnalyzer` / `VisionDescriber` 调用 `llmClient.chat(request)`，改为纯 Prompt 后行为更符合预期（它们本就不该触发工具调用）。

### 3.3 删除冗余日志

`LlmClientManager` 中的 `logRequestParams(messages, toolInstances)` 与 `buildToolOptions(List<Object>)` 因不再使用，可以一并删除，减少 dead code。

---

## 4. 方案架构图

### 4.1 改前（存在循环）

```
┌─────────────────────────────────────────┐
│          LlmClientManager               │
│  (SpiLlmClient 实现)                     │
│                                         │
│  chat() ──> toolRegistry.getToolInstances│
│  chatStream() ──> toolRegistry          │
└────────────┬────────────────────────────┘
             │ 依赖
             ▼
┌─────────────────────────────────────────┐
│           ToolRegistry                  │
│  扫描 @ToolService / @Tool 工具 Bean      │
│  包含 KnowledgeTool                     │
└─────────────────────────────────────────┘
             ▲
             │ KnowledgeAnalyzer 需要 SpiLlmClient
┌────────────┴────────────────────────────┐
│         KnowledgeAnalyzer               │
│  调用 llmClient.chat(...) 做分析          │
└─────────────────────────────────────────┘
```

### 4.2 改后（循环解除）

```
┌─────────────────────────────────────────┐
│          LlmClientManager               │
│  (纯 SpiLlmClient 实现)                  │
│                                         │
│  chat() ──> 构建 Prompt + 共享上下文      │
│  chatStream() ──> streamWithTools()     │
│  chatWithTools() ──> 接收 ToolCallback[] │
│  streamWithTools() ──> 接收 ToolCallback[]│
└─────────────────────────────────────────┘
             ▲
             │ SpiLlmClient
┌────────────┴────────────────────────────┐
│  KnowledgeAnalyzer / VisionDescriber    │
│  AgentExecutor / StreamingAgentExecutor │
└─────────────────────────────────────────┘
             ▲
             │ 需要工具时由 AgentExecutor 传入
┌────────────┴────────────────────────────┐
│           ToolSubSystem                 │
│  从 ToolRegistry 收集 ToolCallback       │
└─────────────────────────────────────────┘
             ▲
             │ 扫描 @ToolService
┌────────────┴────────────────────────────┐
│           ToolRegistry                  │
│  包含 KnowledgeTool 等工具 Bean           │
└─────────────────────────────────────────┘
             ▲
             │ 运行时 getBean(ToolRegistry.class)
┌────────────┴────────────────────────────┐
│      OpenAiLlmClientProvider            │
│  buildChatClient() 注册 Advisors         │
└─────────────────────────────────────────┘
```

```
┌─────────────────────────────────────────┐
│          LlmClientManager               │
│  (纯 LLM 客户端，无工具概念)               │
│                                         │
│  chat()              纯 Prompt          │
│  chatStream()        纯 Prompt          │
│  chatWithTools(...)  接收 ToolCallback[] │
│  streamWithTools(...) 接收 ToolCallback[]│
└─────────────────────────────────────────┘
             ▲
             │ SpiLlmClient
┌────────────┴────────────────────────────┐
│  KnowledgeAnalyzer / VisionDescriber    │
│  AgentExecutor / StreamingAgentExecutor │
└─────────────────────────────────────────┘
             ▲
             │ 需要工具时，由 AgentExecutor 从 ToolSubSystem 获取
┌────────────┴────────────────────────────┐
│           ToolSubSystem                 │
│  从 ToolRegistry 收集 ToolCallback       │
└─────────────────────────────────────────┘
             ▲
             │ 扫描 @ToolService
┌────────────┴────────────────────────────┐
│           ToolRegistry                  │
│  包含 KnowledgeTool 等工具 Bean           │
└─────────────────────────────────────────┘
```

---

## 5. 备选方案与对比

| 方案 | 思路 | 优点 | 缺点 | 推荐度 |
|------|------|------|------|--------|
| **A. 移除 `ToolRegistry` 出 `LlmClientManager`** | 让 LLM 客户端只负责 LLM，工具由调用方传入 | 根治循环；模块分层清晰；改动小 | 需要确认 `chat()` 调用方不需要工具 | ⭐ 推荐 |
| B. `KnowledgeAnalyzer` 注入 `ObjectProvider<SpiLlmClient>` | 延迟解析 `LlmClientManager` | 改动最小 | 未解决根本分层问题；Spring 启动仍可能暴露代理；历史已证明 `@Lazy` 对类似场景不可靠 | 不推荐 |
| C. 把 `KnowledgeTool` 迁回 `meta-claw-tool` | 让 knowledge 包与 `ToolRegistry` 不在同一模块 | 不改变 Spring 循环，只是 Maven 模块整理 | 循环依然存在；且当前工作树 knowledge 已在 core | 不解决本问题 |
| D. `LlmClientManager` 拆成两个 Bean | 一个纯 LLM，一个工具感知 | 也可以切断循环 | 引入两个 LLM client 概念，增加认知负担；当前调用方已经区分 `chat` 和 `chatWithTools`，不需要再拆 Bean | 过度设计 |
| E. `KnowledgeAnalyzer` 直接依赖 `LlmClientProviderManager` | 绕过 `SpiLlmClient` | 也能切断循环 | 破坏 `SpiLlmClient` 抽象；`KnowledgeAnalyzer` 需要处理 ProviderConfig / ChatClient 细节 | 不推荐 |

**结论**：方案 A 在改动量、可维护性、架构清晰度上都是最优解。

---

## 6. 影响范围分析

### 6.1 生产代码

| 文件 | 影响 |
|------|------|
| `meta-claw-core/src/main/java/meta/claw/core/runtime/LlmClientManager.java` | 移除 `ToolRegistry` 字段；`chat()` / `chatStream()` 改为纯 Prompt；删除 `buildToolOptions(List<Object>)`、基于 `toolInstances` 的日志 |
| 其他文件 | 无改动，调用方已按新契约使用 |

### 6.2 测试

| 测试 | 影响 |
|------|------|
| `LlmClientManager*` 相关测试 | 若存在断言 `chat()` 会携带工具的测试，需要更新或删除 |
| `AgentExecutorTest` / `StreamingAgentExecutorTest` | 无影响，继续使用 `chatWithTools` / `streamWithTools` |
| `KnowledgeToolTest` / `KnowledgeAnalyzerTest` | 无影响，继续调用 `chat()` |
| `./init.sh` P0 基线 | 重新执行并确认通过 |

### 6.3 行为等价性

- `AgentExecutor` / `StreamingAgentExecutor` 的工具调用：通过 `chatWithTools` / `streamWithTools` 传 `ToolCallback[]`，行为与改前一致。
- `KnowledgeAnalyzer` / `VisionDescriber`：调用 `chat()`，改前虽然内部带了工具列表，但知识分析 prompt 不会触发工具调用；改后去掉工具列表，行为等价甚至更干净。
- `chatStream()`：主链路未使用，保留接口但改为纯 Prompt，无风险。

---

## 7. 实施步骤

1. **修改 `LlmClientManager.java`**
   - 删除 `ToolRegistry` 字段与 import。
   - `chat()` 中删除 `toolRegistry.getToolInstances()` 和 `buildToolOptions(toolInstances)`，改为 `new Prompt(messages)`。
   - `chatStream()` 中同样删除工具相关逻辑，改为 `new Prompt(messages)`。
   - 删除 `logRequestParams(messages, toolInstances)` 与 `buildToolOptions(List<Object>)` 方法（若不再被其他代码使用）。

2. **运行编译**
   ```bash
   mvn -pl meta-claw-core -am compile -q
   ```

3. **运行 core 模块测试**
   ```bash
   mvn -pl meta-claw-core test -q
   ```

4. **运行全量验证**
   ```bash
   ./init.sh
   ```

5. **验证 `meta-claw-bootstrap` 启动**
   ```bash
   mvn -pl meta-claw-bootstrap spring-boot:run -DskipTests
   ```
   确认 Tomcat 正常启动，无循环依赖报错。

6. **更新状态文件**
   - `claude-progress.md`
   - `feature_list.json`
   - `clean-state-checklist.md`

---

## 8. 风险与缓解

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| 有未发现的调用方依赖 `chat()` 的工具能力 | 中 | `chat()` 返回的 `toolCalls` 为空，影响工具触发 | 全局 grep 确认只有 `KnowledgeAnalyzer` / `VisionDescriber` 调用 `chat()`；若未来需要工具，调用方应改用 `chatWithTools()` |
| `chatStream()` 仍有隐藏调用方 | 低 | 流式输出不带工具 | 已通过 grep 确认主链路未使用；保留接口但纯 Prompt 化 |
| 删除 `logRequestParams` 丢失调试日志 | 低 | 排查时缺少工具列表日志 | 调用方（`AgentExecutor`）可在循环前自行记录 `tools` 列表 |
| Spring 上下文仍有其他循环依赖 | 低 | 启动继续失败 | 本方案明确切断报错环中的关键边；启动成功后若报其他环，再针对性处理 |

---

## 9. 结论

循环依赖的根因不是 knowledge 包所在模块，而是 **`LlmClientManager` 作为底层 LLM 客户端反向依赖了上层的 `ToolRegistry`**。推荐通过**移除 `ToolRegistry` 注入、把 `chat()` / `chatStream()` 改为纯 Prompt、让工具装配留在调用方**来彻底解决。

该方案：
- 符合“底层基础设施不依赖上层业务”的分层原则；
- 与现有 `chatWithTools(..., ToolCallback[])` / `streamWithTools(..., ToolCallback[])` 接口保持一致；
- 不需要 `@Lazy`、`ObjectProvider` 等权宜之计；
- 改动量小，验证路径清晰。
