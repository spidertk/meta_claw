# Vessel Runtime 进化设计：从单次调用到多步 Agent 运行时

> 对比分析 Python Expert 实现与 Java Vessel 实现，提出 Java 侧能力补齐方案。  
> 日期：2026-05-22  
> 范围：`meta-claw-core` 运行时层  

---

## 1. Executive Summary

**一句话概括：Java Vessel 当前是"单次 LLM 调用包装器"，Python Expert 是"完整的多步 Agent 运行时"。**

Python 侧的 `ExpertRuntime`（837 行）已经形成了包含记忆、知识、技能、工具循环、流式输出、统计观测、定时任务、热进化在内的完整体系；而 Java 侧的 `VesselRuntime`（74 行）目前仅封装了 `PromptContextManager` + `SystemPromptBuilder` + `LlmClientManager` 的三件套，完成一次单轮对话后即返回结果，没有工具调用循环，没有流式输出，没有子系统编排能力。本文档旨在通过差距分析、抽象升级和实施路径，指导 Java Vessel 向真正的 Agent 运行时演进。

---

## 2. Capability Matrix（能力矩阵）

| 维度 | 能力项 | Python Expert (`expert_manager.py` + `expert_runtime.py` + `agent_loop.py`) | Java Vessel (`VesselManager` + `VesselRuntime` + `AgentLoop`) |
|------|--------|-----------------------------------------------------------------------------|---------------------------------------------------------------|
| **Manager** | 配置缓存 | ✅ `dict[str, ExpertInfo]` 内存缓存 | ✅ `ConcurrentHashMap<String, VesselConfig>` |
| | 自动刷新/Watch | ✅ `start_watching()` + `asyncio` 定时轮询（30s） | ❌ 无，需手动重启加载 |
| | 生命周期（创建/删除/重载） | ✅ `create()` / `delete()` / `reload()`，含模板渲染、安全校验 | ❌ 无，仅扫描加载 |
| | 文件监控 | ✅ 监控 `EXPERT.md` 变化，自动 `reload()` | ❌ 无 |
| | 安全校验 | ✅ 名称正则校验 + 路径解析防目录遍历 | ❌ 无 |
| **Runtime** | 记忆子系统 | ✅ `MemoryManager` + `MemoryStore`，支持 workspace 持久化 | ⚠️ `ShortMemoryManager` 存在但 **Runtime 未使用** |
| | 知识库子系统 | ✅ `KnowledgeManager`，支持目录扫描、知识检索工具 | ❌ 无 |
| | 技能子系统 | ✅ `SkillsManager` 加载系统级 + Expert 级技能 | ❌ 无 |
| | 工具调用循环 | ✅ `kosong.step()` 驱动，最大 500 步，ReAct 语义 | ❌ 单次 `llmClientManager.chat()` |
| | 流式输出 | ✅ `stream_callback` + `thinking_callback` 双轨回调 | ❌ 无 |
| | 统计/可观测性 | ✅ `RuntimeStats`：token、steps、tool_calls、session_start | ❌ 无 |
| | Cron 定时任务 | ✅ `CronService` + 上下文注入 + return_address 回注 | ❌ 无 |
| | 热进化（Evo） | ✅ `EvoCoreRegistry` + `EvoToolRunner`，运行时热加载工具 | ❌ 无 |
| | 媒体附件 | ✅ `media_list` 支持 image/audio/video/file，自动转 data URL | ❌ 无 |
| | LLM I/O 日志 | ✅ `llm_debug` 目录，按 step 记录 input/output JSON | ❌ 无 |
| | 同步/异步双接口 | ✅ `chat()` 同步 + `chat_async()` 异步，含 event loop 检测 | ❌ 仅同步 |
| | 对话持久化 | ✅ `save_conversation()` 保存到 workspace | ❌ 无 |
| **Loop** | 事件驱动 | ✅ EventBus（Guava）订阅 `UserMessageReceived` | ✅ EventBus（Guava）订阅 `UserMessageReceived` |
| | 路由策略 | ✅ 当前也是首条匹配，但 `Expert` 体系支持按名路由 | ⚠️ `determineTargetVessel()` 返回第一个 Vessel |
| | 错误处理 | ✅ 异常捕获 + ERROR 事件发布 | ✅ 异常捕获 + ERROR 事件发布 |
| | 运行时内部事件 | ✅ `tool_call_started`, `step_completed` 等走 EventBus | ❌ 无内部事件 |

---

## 3. Core Gaps（核心差距分析）

### Gap 1: VesselRuntime 是单次调用，没有 ReAct/Tool Loop

**现状：** `VesselRuntime.chat()` 调用链路：
```java
String systemPrompt = resolveSystemPrompt(vesselId);
SpiChatRequest request = SpiChatRequest.builder()
    .messages(llmClientManager.buildLlmRequest(vesselId, sessionId, systemPrompt))
    .build();
SpiChatResponse response = llmClientManager.chat(request);
return new Reply(ReplyType.TEXT, content);
```
这是**单轮请求-响应**，LLM 返回文本即结束。如果 LLM 输出中包含 `tool_calls`，Java 侧没有任何机制去解析、执行、将结果回注到上下文并继续下一轮对话。

**Python 对标：**
```python
while step_count < max_steps:
    step_result = await kosong.step(...)
    if step_result.tool_calls:
        tool_results = await step_result.tool_results()
        history.append(tool_response)  # 回注结果
        continue  # 继续下一轮
    else:
        final_message = ...
        break
```

### Gap 2: 没有 Knowledge 子系统

Python `ExpertRuntime` 初始化时创建 `KnowledgeManager`，并在 system prompt 中注入知识目录上下文：
```python
self.knowledge_manager = KnowledgeManager(self.expert_dir, llm_provider=self, workspace_dir=self.workspace_dir)
# 在 _create_prompt_context() 中：
knowledge_context=self.load_knowledge_context()
```

Java 侧没有任何知识文件扫描、索引或检索能力。`meta-claw-store` 模块虽然存在，但当前仅提供记忆存储，没有知识库。

### Gap 3: 没有 Skill 子系统

Python 通过 `SkillsManager` 加载系统级技能目录 + Expert 级技能：
```python
system_skills_dirs = [d for d in self.config.system_skills_dir if d.exists()]
self.skills_manager = SkillsManager(self.expert_dir, system_skills_dirs=system_skills_dirs)
self.skills = self.skills_manager.load()
```

技能在 system prompt 中以结构化形式注入，增强 Expert 的指令遵循能力。Java 侧 `SystemPromptBuilder` 目前仅基于 `PromptContext` 构建，没有技能加载和注入逻辑。

### Gap 4: 没有 Evo（热进化）子系统

Python 的 `EvoCoreRegistry` 支持在运行时动态加载、审核、注入新的工具代码：
```python
self._evo_registry = EvoCoreRegistry(evo_dir)
self._evo_tool_runner = EvoToolRunner(self.toolset._registry)
self._evo_registry.subscribe(self._evo_tool_runner.on_component_loaded)
```

Java 侧的 `ToolRegistry` 虽然支持 `register()` / `unregister()` / `reregister()` 运行时热注入，但没有文件监听、组件审核、版本管理的进化闭环。

### Gap 5: 没有 Cron 子系统

Python `ExpertRuntime` 通过全局 `CronService` 支持定时任务触发，并在消息层面注入 cron 上下文：
```python
cron_prefix = f"[定时任务触发 - 任务名: {cron_context.get('cron_name')}...]"
# 同时注入 return address，让 message 工具知道结果发到哪里
message_tool.set_cron_return_address(return_channel, return_chat_id)
```

Java 侧没有任何定时任务调度和上下文注入能力。

### Gap 6: 没有 Streaming 支持

Python 通过 `kosong.step()` 的 `on_message_part` 回调实现流式输出，同时区分 `stream_callback`（文本）和 `thinking_callback`（推理内容）：
```python
def on_part(part):
    if hasattr(part, 'text'):
        stream_callback(part.text)
    elif hasattr(part, 'think'):
        thinking_callback(part.think)
```

Java 侧 `SpiChatResponse` 目前仅返回完整字符串 `content()`，没有分片回调接口。

### Gap 7: VesselManager 没有 Auto-reload

Python `ExpertManager` 支持对活跃 Expert 启动监控，每 30 秒自动检测 `EXPERT.md` 变更并重新加载：
```python
async def _refresh_loop(self):
    while self._running:
        await asyncio.sleep(self._refresh_interval)
        for name in list(self._watching_experts):
            self.reload(name)
```

Java `VesselManager` 仅在启动时通过 `loadVessels()` 扫描一次，后续配置变更必须重启服务。

### Gap 8: 没有 RuntimeStats / 可观测性

Python 的 `RuntimeStats` 实时追踪：
- `total_tokens`（input/output/cache_read/cache_creation）
- `total_steps`（对话轮次）
- `total_tool_calls`（工具调用次数）
- `session_start`（会话开始时间）

Java 侧没有任何运行时指标收集，LLM I/O 也没有调试日志。

### Gap 9: AgentLoop 路由过于简单

Python 侧虽然也是简单路由，但其体系是按 Expert **名称**精确路由（ Gateway → Expert 一对一）。Java 侧的 `determineTargetVessel()` 直接返回列表中的第一个 Vessel，在多个 Vessel 并存的场景下没有意图识别、权重匹配或负载均衡能力。

---

## 4. Abstraction Proposal（抽象升级建议）

### 4.1 引入 `VesselSubSystem` SPI 接口

所有子系统统一实现此接口，`VesselRuntime` 从"自己干所有事"变为"编排子系统"。

```java
package meta.claw.core.runtime.subsystem;

/**
 * Vessel 子系统 SPI 接口。
 * 所有子系统（Memory, Knowledge, Skill, Tool, Cron, Evo, Stats）实现此接口，
 * 由 VesselRuntime 统一编排生命周期和对话上下文注入。
 */
public interface VesselSubSystem {

    /** 子系统唯一标识，如 "memory", "knowledge", "skill" */
    String name();

    /** 初始化：VesselRuntime 创建时调用，传入 Vessel 级上下文 */
    void initialize(VesselContext vesselContext);

    /** 每次对话前调用，子系统向 PromptContext 注入内容 */
    void contribute(PromptContext.Builder contextBuilder);

    /** 对话结束后调用，子系统保存状态或清理资源 */
    void finalize(VesselExecutionContext executionContext);

    /** 子系统优先级，数值越小越早执行 contribute */
    default int priority() { return 100; }
}
```

### 4.2 `VesselRuntime` 改为子系统编排器

```java
@Slf4j
@Component
public class VesselRuntime {

    @Autowired
    private PromptContextManager promptContextManager;
    @Autowired
    private SystemPromptBuilder systemPromptBuilder;
    @Autowired
    private LlmClientManager llmClientManager;
    @Autowired
    private ToolRegistry toolRegistry;  // 已有组件

    /** 所有通过 Spring 注入的子系统，按 priority 排序 */
    @Autowired
    private List<VesselSubSystem> subSystems;

    /** 工具调用循环，Phase 1 核心新增 */
    @Autowired
    private VesselToolLoop toolLoop;

    public Reply chat(String vesselId, String sessionId, String userMessage) {
        // 1. 创建单次对话上下文（替代 Python 的 RuntimeStats + 历史记录）
        VesselExecutionContext ctx = VesselExecutionContext.builder()
            .vesselId(vesselId)
            .sessionId(sessionId)
            .startTime(Instant.now())
            .build();

        // 2. 收集子系统贡献的 prompt 上下文
        PromptContext promptContext = promptContextManager.create(vesselId);
        for (VesselSubSystem sub : subSystems) {
            sub.contribute(promptContext.toBuilder());
        }
        String systemPrompt = systemPromptBuilder.build(promptContext);

        // 3. 构建 LLM 请求（含历史消息）
        List<SpiMessage> messages = llmClientManager.buildLlmRequest(vesselId, sessionId, systemPrompt);
        messages.add(SpiMessage.user(userMessage));

        // 4. 进入工具调用循环（核心升级点）
        SpiChatResponse response = toolLoop.execute(
            vesselId, sessionId, systemPrompt, messages, ctx
        );

        // 5. 子系统收尾
        for (VesselSubSystem sub : subSystems) {
            sub.finalize(ctx);
        }

        return new Reply(ReplyType.TEXT, response.content());
    }
}
```

### 4.3 引入 `VesselExecutionContext` 作为单次对话载体

替代 Python 的 `RuntimeStats`，同时承载会话级元数据和跨 step 状态：

```java
@Builder
@Getter
public class VesselExecutionContext {
    private final String vesselId;
    private final String sessionId;
    private final Instant startTime;
    private final List<StepRecord> steps = new ArrayList<>();
    private final AtomicInteger totalToolCalls = new AtomicInteger(0);
    private final AtomicInteger totalTokensIn = new AtomicInteger(0);
    private final AtomicInteger totalTokensOut = new AtomicInteger(0);

    public void recordStep(StepRecord step) { steps.add(step); }
    public int stepCount() { return steps.size(); }
}

@Builder
public record StepRecord(
    int stepNumber,
    List<ToolCallRecord> toolCalls,
    TokenUsage usage,
    Duration latency,
    String thinkingContent
) {}
```

### 4.4 借鉴 Python `ExpertToolset`，复用 Java `ToolRegistry`

Python 侧的 `ExpertToolset` 是一个聚合工具集合，包含 bash、memory_store、knowledge、process、message、mcp 等工具。Java 侧已有 `ToolRegistry`（`meta-claw-core/src/main/java/meta/claw/core/tool/registry/ToolRegistry.java`），支持：
- `@PostConstruct` 自动扫描 `@Tool` 注解方法
- 运行时 `register()` / `unregister()` / `reregister()` 热注入
- `SpiToolDefinition` + `ToolMethod` 执行模型

**建议：** 不重新造工具注册表，而是在 `VesselToolLoop` 中集成 `ToolRegistry` 作为工具发现和执行入口。各工具作为 Spring `@Component` 独立存在，通过 `@Tool` 注解暴露能力。

---

## 5. Optimization Proposal（优化建议）

### 5.1 Tool Loop：实现 `VesselToolLoop`

模仿 `kosong.step()` 语义，实现 LLM 工具调用循环：

```java
@Slf4j
@Component
public class VesselToolLoop {

    @Autowired
    private LlmClientManager llmClientManager;
    @Autowired
    private ToolRegistry toolRegistry;
    @Autowired
    private EventBusWrapper eventBus;

    @Value("${vessel.runtime.max-steps:50}")
    private int maxSteps;

    public SpiChatResponse execute(String vesselId, String sessionId,
                                   String systemPrompt, List<SpiMessage> messages,
                                   VesselExecutionContext ctx) {
        for (int step = 1; step <= maxSteps; step++) {
            SpiChatRequest request = SpiChatRequest.builder()
                .systemPrompt(systemPrompt)
                .messages(new ArrayList<>(messages))
                .tools(toolRegistry.getToolDefinitions())  // 注入可用工具列表
                .build();

            long start = System.currentTimeMillis();
            SpiChatResponse response = llmClientManager.chat(request);
            ctx.recordStep(StepRecord.builder()
                .stepNumber(step)
                .usage(response.usage())
                .latency(Duration.ofMillis(System.currentTimeMillis() - start))
                .build());

            // 检查是否有 tool_calls
            List<SpiToolCall> toolCalls = response.toolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                // 无工具调用，最终答案
                return response;
            }

            // 有 tool_calls，执行并回注结果
            ctx.getTotalToolCalls().addAndGet(toolCalls.size());
            eventBus.post(new ToolCallStartedEvent(vesselId, sessionId, step, toolCalls));

            for (SpiToolCall tc : toolCalls) {
                ToolResult result = executeToolCall(tc);
                messages.add(SpiMessage.toolResult(tc.id(), result.content()));
            }

            eventBus.post(new StepCompletedEvent(vesselId, sessionId, step, ctx.stepCount()));
        }

        log.warn("Vessel {} reached max steps ({}) without final answer", vesselId, maxSteps);
        return SpiChatResponse.builder()
            .content("抱歉，处理时间过长，请简化您的问题后重试。")
            .build();
    }

    private ToolResult executeToolCall(SpiToolCall tc) {
        ToolRegistry.ToolMethod tm = toolRegistry.findMethod(tc.name());
        if (tm == null) {
            return ToolResult.error("Unknown tool: " + tc.name());
        }
        // 反射调用 + 参数反序列化
        return ToolExecutor.invoke(tm, tc.arguments());
    }
}
```

### 5.2 Streaming：引入 `StreamConsumer` 回调接口

支持 thinking/stream 双轨回调，兼容 Spring AI 的 `Stream<String>` 能力：

```java
public interface StreamConsumer {
    void onText(String textFragment);
    void onThinking(String thinkingFragment);
    void onToolCall(ToolCallStartedEvent event);
    void onComplete(String fullContent);
    void onError(Throwable error);
}

// 在 VesselRuntime 中增加 streaming 入口
public void chatStreaming(String vesselId, String sessionId, String userMessage,
                          StreamConsumer consumer) {
    // ...
    toolLoop.executeStreaming(vesselId, sessionId, systemPrompt, messages, ctx, consumer);
}
```

### 5.3 Auto-reload：VesselManager 增加文件监控

通过 Java `WatchService` 或 Spring `@Scheduled` 实现：

```java
@Slf4j
@Component
public class VesselConfigWatcher {

    @Autowired
    private VesselManager vesselManager;

    @Scheduled(fixedDelay = 30_000)
    public void refreshWatchingVessels() {
        for (String vesselId : vesselManager.getWatchingVesselIds()) {
            try {
                vesselManager.reload(vesselId);
                log.info("Auto-reloaded vessel: {}", vesselId);
            } catch (Exception e) {
                log.warn("Failed to auto-reload vessel {}: {}", vesselId, e.getMessage());
            }
        }
    }
}
```

### 5.4 RuntimeStats：引入 `VesselExecutionMetrics`

```java
@Component
public class VesselExecutionMetrics {

    private final MeterRegistry meterRegistry;  // Micrometer

    public void record(VesselExecutionContext ctx) {
        meterRegistry.counter("vessel.steps", "vessel", ctx.getVesselId())
            .increment(ctx.stepCount());
        meterRegistry.counter("vessel.tool_calls", "vessel", ctx.getVesselId())
            .increment(ctx.getTotalToolCalls().get());
        meterRegistry.timer("vessel.latency", "vessel", ctx.getVesselId())
            .record(Duration.between(ctx.getStartTime(), Instant.now()));
    }
}
```

### 5.5 Routing：AgentLoop 路由策略模式化

将 `determineTargetVessel()` 抽取为策略接口：

```java
public interface VesselRouter {
    String route(UserMessageReceived event, List<VesselConfig> candidates);
}

// 当前默认实现
@Component
public class FirstAvailableVesselRouter implements VesselRouter { ... }

// 未来扩展
@Component
public class IntentBasedVesselRouter implements VesselRouter { ... }
```

### 5.6 EventBus 增强：VesselRuntime 内部事件标准化

Python 侧通过日志和回调暴露内部状态；Java 侧应利用已有的 Guava EventBus 发布结构化事件：

| 事件 | 触发时机 | 消费者 |
|------|---------|--------|
| `ToolCallStartedEvent` | LLM 返回 tool_calls 时 | 日志、Metrics、Gateway |
| `StepCompletedEvent` | 单步执行完成（含工具结果回注） | 日志、Debug UI |
| `VesselStatsUpdatedEvent` | token/steps 更新时 | Metrics、监控面板 |
| `VesselResponseChunkEvent` | 流式输出分片时 | Gateway SSE 推送 |

---

## 6. Implementation Path（实施路径）

### Phase 1: Immediate（立即）— Tool Loop + Streaming callbacks

**目标：** 让 `VesselRuntime` 从单次调用升级为多步 Agent。

1. 实现 `VesselToolLoop`（循环：LLM 调用 → 有 tool_calls? → `ToolRegistry` 执行 → 结果回注 → 继续）
2. 扩展 `SpiChatResponse` / `SpiChatRequest` 支持 `toolCalls` 和 `toolDefinitions` 字段
3. 实现 `ToolExecutor` 统一执行入口（参数 JSON → Java 对象反序列化 → 反射调用）
4. 引入 `StreamConsumer` 接口，在 `LlmClientManager` 中增加 `chatStreaming()` 能力
5. **验证：** 编写端到端测试：用户问"1+1 等于几"→ Vessel 调用 CalculatorTool → 返回结果

### Phase 2: Short-term（短期）— KnowledgeRuntime + SkillRuntime

**目标：** 补齐知识和技能能力。

1. 在 `meta-claw-store` 中新增 `KnowledgeManager`（知识文件扫描、索引、检索）
2. 实现 `KnowledgeSubSystem implements VesselSubSystem`，在 `contribute()` 中向 prompt 注入知识上下文
3. 实现 `SkillManager` 和 `SkillSubSystem`，支持从 `skills/` 目录加载技能描述文件
4. 扩展 `SystemPromptBuilder` 接收子系统贡献内容并拼接为最终 system prompt
5. **验证：** 创建一个带知识文件的 Vessel，提问知识库中的内容，确认 context 被正确注入

### Phase 3: Medium-term（中期）— VesselManager auto-reload + RuntimeStats

**目标：** 可观测性和动态配置。

1. `VesselManager` 增加 `watch(String vesselId)` / `unwatch(String vesselId)` API
2. 通过 `@Scheduled` 或 `WatchService` 实现 `vessel.md` 变更检测
3. 引入 `VesselExecutionMetrics`（Micrometer），对接 `VesselExecutionContext`
4. 增加 `VesselExecutionMetrics` 的 HTTP `/actuator` 端点暴露
5. **验证：** 修改 `vessel.md` 后 30 秒内无需重启即可观察到行为变化

### Phase 4: Long-term（长期）— Cron + Evo

**目标：** 高级能力。

1. 设计 `CronService` 调度层，支持基于 `vessel.md` 中 `cron` 配置自动生成触发器
2. Cron 触发时构造 `CronContext`，通过 EventBus 投递 `UserMessageReceived` 事件
3. 在 `VesselRuntime` 中识别 cron 上下文并注入前缀提示
4. `EvoCoreRegistry`：基于 `ToolRegistry.reregister()` 能力，增加文件监听 + 代码审核 + 版本管理闭环
5. **验证：** 配置一个每 5 分钟触发的 Vessel cron 任务，验证自动触发和结果回调

---

## 7. Key Design Decisions（关键设计决策）

| 决策 | 选择 | 理由 |
|------|------|------|
| 容器管理 | 保持 Spring 容器管理，所有子系统用 `@Component` | 与现有架构一致，支持依赖注入和生命周期管理 |
| 跨组件通信 | 保持 EventBus（Guava） | 已有成熟基础设施，AgentLoop 已深度使用，新增内部事件可无缝集成 |
| 功能范围 | 不盲目复制 Python 的所有功能 | Python Expert 有 800+ 行运行时，Java 侧应优先补齐主链路（Tool Loop + Memory + Knowledge），避免过度工程 |
| Runtime 作用域 | `VesselRuntime` 保持 Spring 单例，但单次对话创建 `VesselExecutionContext` | 单例节省资源，`VesselExecutionContext` 保证对话隔离，与 Python 的每 Expert 单例 + 单次 stats 对象模式等价 |
| 已有模块复用 | 复用 `ToolRegistry`、`ShortMemoryManager` | `ToolRegistry` 已有热注入能力；`ShortMemoryManager` 已存在但未接入 Runtime，Phase 1/2 应将其整合进 `MemorySubSystem` |
| 工具执行 | `ToolExecutor` 统一封装反射调用 + 参数映射 | 避免 `VesselToolLoop` 直接处理反射细节，保持循环逻辑的纯粹性 |
| 流式实现 | `StreamConsumer` 回调接口，而非阻塞式 Iterator | 与现有 EventBus 异步模型一致，便于 Gateway SSE 推送 |

---

## 8. Appendix: 文件规模对比

| 文件 | 语言 | 行数 | 职责 |
|------|------|------|------|
| `expert_manager.py` | Python | 424 | 缓存、自动刷新、生命周期、文件监控、安全校验 |
| `expert_runtime.py` | Python | 837 | Memory、Knowledge、Skill、Tool Loop、流式、统计、Cron、Evo、同步/异步双接口 |
| `VesselManager.java` | Java | 111 | 基础扫描加载、`ConcurrentHashMap` 缓存、简单 CRUD |
| `VesselRuntime.java` | Java | 74 | PromptContextManager + SystemPromptBuilder + LlmClientManager，单次调用 |
| `AgentLoop.java` | Java | 147 | EventBus 订阅、简单路由、单次 runtime.chat()、发布 VesselResponseReady |

> **结论：** Python Expert 运行时代码量约为 Java Vessel 运行时的 **7.6 倍**（1261 vs 166 行），差距主要体现在 Tool Loop、子系统编排和可观测性上。Phase 1 的目标是将 Java VesselRuntime 从 74 行扩展到具备 Tool Loop 能力的 300+ 行运行时。
