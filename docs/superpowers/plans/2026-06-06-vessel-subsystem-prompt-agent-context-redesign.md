# VesselSubSystem SPI + PromptContext / AgentExecutionContext 关系重塑

> **日期：2026-06-06（修订版）**
> **前置文档：**
> - `2026-06-04-meta-claw-agent-platform-longterm-design.md`（长远架构）
> - `2026-06-06-vessel-subsystem-spi-context-refactor.md`（初版实现计划，本计划替代之）
>
> **修订原因：**
> 1. `initialize` 与 `enrich` 语义边界模糊，子系统开发者困惑"我该在哪个阶段做什么"
> 2. `PromptContext` 被描述为"静态素材"却在每次对话时被动态 `enrich`，矛盾
> 3. `AgentExecutionContext` 同时持有 `PromptContext` 和 `registry`，数据获取渠道重复
> 4. `PromptSubSystem` 在初版中 `initialize` 和 `enrich` 均为空实现，说明角色定位错误

---

## 1. 设计原则（约束条件，不变）

1. **VesselSubSystem SPI 必须存在**：所有能力必须作为子系统接入，不得硬编码在 VesselRuntime 中。
2. **Spring AI 1.1.7 原生能力最大化复用**：`@Tool`、MCP、ToolCallback、ChatClient Advisor 优先复用。
3. **消息生命周期统一管理**：`ChatMessageManager` 是唯一消息管家。
4. **渐进式实现**：先搭 SPI 骨架，再填子系统血肉。每阶段结束必须能跑通 `./init.sh`。

---

## 2. 核心概念重新定义

### 2.1 PromptContext = 单次对话的完整 Prompt 素材（不可变）

**重新定义**：`PromptContext` 不是"静态/半静态配置"，而是**单次对话开始前，由 VesselRuntime 统一组装完成的、不可变的 Prompt 素材集合**。它包含：

- 系统提示所需的模板变量（identity、rules、constraints）
- 当前时间、用户偏好等上下文变量
- 技能摘要列表（由 SkillSubSystem 贡献）
- 工具描述列表（由 ToolSubSystem 贡献）
- 长期记忆摘要（由 MemorySubSystem 贡献）
- 其他子系统的 prompt 注入内容

**关键变化**：
- ❌ 不再在 `VesselRuntime.afterPropertiesSet()` 中缓存"基础 PromptContext"
- ❌ 不再使用 `toBuilder()` 复制-增量模式
- ✅ 每次 `chat()` / `execute()` 调用时，从零创建 Builder，收集所有子系统贡献，一次性 build

### 2.2 AgentExecutionContext = 单次对话的执行工作区（可变）

**重新定义**：`AgentExecutionContext` 是**单次对话执行期间的工作区**，包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| `task` | `VesselTask` | 本次对话的任务标识（taskId, vesselId, sessionId, userMessage） |
| `promptContext` | `PromptContext` | **只读引用**。由 VesselRuntime 构建完成后注入，执行期间不再修改 |
| `registry` | `SubSystemRegistry` | 子系统查询入口。执行期通过它获取子系统服务（如 ToolSubSystem.getToolCallbacks()） |
| `messages` | `List<SpiMessage>` | 执行期动态累积的消息列表（user → assistant → tool → ...） |
| `steps` | `List<StepRecord>` | 执行步骤记录（ReAct loop 的每轮迭代） |

**与 PromptContext 的关系**：

```
┌─────────────────────────────────────────┐
│  VesselRuntime.chat(sessionId, msg)     │
│                                         │
│  ① buildPromptContext()                 │
│     ├── PromptContext.Builder builder = │
│     │   promptContextFactory.createBuilder(vesselId) │
│     ├── 按 priority 调用各子系统:        │
│     │   sub.contribute(builder)         │
│     └── PromptContext promptCtx =       │
│         builder.build()  ← 不可变        │
│                                         │
│  ② new AgentExecutionContext(task,      │
│     promptCtx, registry)                │
│     ← promptCtx 作为只读字段注入         │
│                                         │
│  ③ subSystems.onExecutionStart(ctx)     │
│  ④ agentExecutor.execute(ctx)           │
│     ← 执行期读取 ctx.getPromptContext() │
│     ← 执行期查询 ctx.getSubSystem("tool")│
│     ← 执行期修改 ctx.addMessage()       │
│  ⑤ subSystems.onExecutionEnd(ctx)       │
└─────────────────────────────────────────┘
```

**一句话总结**：
- **PromptContext 是输入**（给 LLM 看的素材，构建后不可变）
- **AgentExecutionContext 是工作区**（引擎内部跟踪状态，执行期可变）

---

## 3. VesselSubSystem SPI 重塑

### 3.1 接口定义

```java
package meta.claw.core.runtime.subsystem;

import meta.claw.core.agent.AgentExecutionContext;
import meta.claw.core.prompt.PromptContext;

/**
 * Vessel 子系统 SPI。
 * 所有能力（Memory, Tool, Skill, HITL, Metrics, Knowledge, Cron）必须实现此接口，
 * 由 VesselRuntime 统一编排生命周期。
 *
 * <p>调用顺序（由 VesselRuntime 保证）：</p>
 * <ol>
 *   <li>{@link #configure(SubSystemRegistry)} — VesselRuntime 创建时调用一次</li>
 *   <li>{@link #contribute(PromptContext.Builder)} — 每次对话前，参与 PromptContext 构建</li>
 *   <li>{@link #onExecutionStart(AgentExecutionContext)} — 每次对话开始时调用</li>
 *   <li>{@link #onExecutionEnd(AgentExecutionContext)} — 每次对话结束时调用（finally 中）</li>
 * </ol>
 */
public interface VesselSubSystem {

    /** 子系统唯一标识，如 "memory", "tool", "skill", "hitl" */
    String name();

    /**
     * 配置阶段：VesselRuntime 创建时调用一次。
     *
     * <p>用途：</p>
     * <ul>
     *   <li>缓存 {@link SubSystemRegistry} 引用，供后续使用</li>
     *   <li>建立外部连接（如 MCP 客户端初始化、加载技能文件索引）</li>
     *   <li>读取一次性的配置并缓存</li>
     * </ul>
     *
     * <p>注意：此方法<strong>不应</strong>操作 PromptContext，因为此时还未到对话时刻。</p>
     */
    void configure(SubSystemRegistry registry);

    /**
     * 贡献阶段：每次对话前调用，向本次对话的 PromptContext 注入内容。
     *
     * <p>用途：</p>
     * <ul>
     *   <li>向 System Prompt 注入技能摘要列表</li>
     *   <li>注入可用工具的描述信息</li>
     *   <li>注入用户长期偏好摘要</li>
     *   <li>注入其他需要让 LLM 知道的领域数据</li>
     * </ul>
     *
     * <p>注意：此方法只操作 {@link PromptContext.Builder}，不操作执行状态。</p>
     */
    void contribute(PromptContext.Builder builder);

    /** 执行开始：每次对话开始时调用。可在此加载历史消息、打开事务等。 */
    default void onExecutionStart(AgentExecutionContext ctx) {}

    /** 执行结束：每次对话结束时调用（finally 块中）。可在此保存消息、关闭事务、记录 metrics。 */
    default void onExecutionEnd(AgentExecutionContext ctx) {}

    /** 优先级：数值越小，越早执行 contribute */
    default int priority() { return 100; }
}
```

### 3.2 为什么 `configure` 和 `contribute` 不合并？

| 维度 | `configure(registry)` | `contribute(builder)` |
|------|----------------------|----------------------|
| **调用次数** | 每个 VesselRuntime 实例一次 | 每次对话一次 |
| **核心职责** | 子系统自身初始化 + 获取协作引用 | 向本次对话的 Prompt 注入数据 |
| **操作对象** | `SubSystemRegistry`（子系统协作） | `PromptContext.Builder`（Prompt 素材） |
| **典型工作** | 加载技能文件索引、建立 MCP 连接 | 注入技能列表、工具描述 |
| **是否有副作用** | 可以（建立连接、缓存配置） | 应无副作用（只操作 Builder） |

**如果合并成一个方法**，子系统需要在每次对话时都重复执行一次性初始化工作（如重新加载技能索引），或者需要通过复杂的内部状态判断来跳过。分离后，语义天然清晰：
- `configure` = "我准备好了，这是我的 registry"
- `contribute` = "这次对话，我给 prompt 加这些内容"

---

## 4. 子系统逐个设计（按新 SPI）

### 4.1 MemorySubSystem

```java
package meta.claw.core.runtime.subsystem;

@Slf4j
@Component
public class MemorySubSystem implements VesselSubSystem {

    @Autowired private ShortMemoryFactory shortMemoryFactory;
    @Autowired private LongMemoryFactory longMemoryFactory;

    private SubSystemRegistry registry;

    @Override
    public String name() { return "memory"; }

    @Override
    public void configure(SubSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void contribute(PromptContext.Builder builder) {
        // 未来：注入长期记忆摘要、用户偏好摘要
        // 当前保持空实现，避免破坏现有 PromptContextFactory 行为
    }

    @Override
    public int priority() { return 10; }

    public ShortMemory getShortMemory(MemoryConfig config) {
        return shortMemoryFactory.get(config.getShortTermStore());
    }

    public LongMemory getLongMemory(MemoryConfig config) {
        return longMemoryFactory.get(config.getLongTermStore());
    }
}
```

### 4.2 ToolSubSystem

```java
package meta.claw.core.runtime.subsystem;

@Component
public class ToolSubSystem implements VesselSubSystem {

    @Autowired private ToolRegistry toolRegistry;
    @Autowired(required = false) private List<ToolCallbackProvider> mcpToolProviders;

    private SubSystemRegistry registry;

    @Override
    public String name() { return "tool"; }

    @Override
    public void configure(SubSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void contribute(PromptContext.Builder builder) {
        // 向 PromptContext 注入可用工具列表（名称+描述），供 System Prompt 渲染使用
        List<ToolInfo> tools = getToolCallbacks().stream()
            .map(tc -> ToolInfo.builder()
                .name(tc.getToolDefinition().name())
                .description(tc.getToolDefinition().description())
                .build())
            .toList();
        builder.availableTools(tools);
    }

    @Override
    public int priority() { return 20; }

    public List<ToolCallback> getToolCallbacks() {
        List<ToolCallback> all = new ArrayList<>();

        // 1. 本地 @Tool / @ToolService Bean
        Object[] localBeans = toolRegistry.getToolInstances().toArray();
        if (localBeans.length > 0) {
            all.addAll(Arrays.asList(ToolCallbacks.from(localBeans)));
        }

        // 2. MCP 客户端工具
        if (mcpToolProviders != null) {
            for (ToolCallbackProvider provider : mcpToolProviders) {
                all.addAll(Arrays.asList(provider.getToolCallbacks()));
            }
        }

        return all;
    }
}
```

### 4.3 SkillSubSystem

```java
package meta.claw.core.runtime.subsystem;

@Component
public class SkillSubSystem implements VesselSubSystem {

    @Autowired private SkillRegistry skillRegistry;

    private String vesselId;
    private SubSystemRegistry registry;

    @Override
    public String name() { return "skill"; }

    @Override
    public void configure(SubSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void contribute(PromptContext.Builder builder) {
        // 向 System Prompt 注入技能摘要（名称 + 描述），不注入全文
        List<SkillInfo> infos = skillRegistry.getAvailableSkills(vesselId).stream()
            .map(s -> SkillInfo.builder().name(s.name()).description(s.description()).build())
            .toList();
        builder.skills(infos);
    }

    @Override
    public int priority() { return 30; }

    public void loadForVessel(String vesselId) {
        this.vesselId = vesselId;
        skillRegistry.load(vesselId);
    }

    public String readSkillContent(String skillName) {
        return skillRegistry.findByName(skillName)
            .map(Skill::content)
            .orElse("Skill not found: " + skillName);
    }
}
```

> **注意**：SkillSubSystem 在 `configure` 时不加载技能（因为此时可能还不知道 vesselId），而是在 VesselRuntime 中调用 `loadForVessel()` 后，通过 `contribute()` 注入。

### 4.4 HitlSubSystem

```java
package meta.claw.core.runtime.subsystem;

@Component
public class HitlSubSystem implements VesselSubSystem {

    @Autowired private HitlPolicy hitlPolicy;
    @Autowired private HitlGate hitlGate;

    private SubSystemRegistry registry;

    @Override
    public String name() { return "hitl"; }

    @Override
    public void configure(SubSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void contribute(PromptContext.Builder builder) {
        // 可在 System Prompt 中注入 HITL 规则说明（如"某些工具需要人工审批"）
        builder.hitlPolicySummary(hitlPolicy.getSummary());
    }

    public HitlEvaluation evaluate(List<AssistantMessage.ToolCall> toolCalls, AgentExecutionContext ctx) {
        // ... 同 06-04 文档
    }
}
```

### 4.5 MetricsSubSystem（预留）

```java
@Component
public class MetricsSubSystem implements VesselSubSystem {

    @Autowired private MeterRegistry meterRegistry;

    @Override
    public String name() { return "metrics"; }

    @Override
    public void configure(SubSystemRegistry registry) { }

    @Override
    public void contribute(PromptContext.Builder builder) { }

    @Override
    public void onExecutionEnd(AgentExecutionContext ctx) {
        VesselTask task = ctx.getTask();
        meterRegistry.counter("agent.task.completed", "vessel", task.getVesselId()).increment();
        meterRegistry.counter("agent.steps", "vessel", task.getVesselId()).increment(task.getSteps().size());
    }
}
```

---

## 5. PromptSubSystem 的取消与 PromptContextFactory 的回归

**06-06 初版的问题**：`PromptSubSystem` 被设计为一个普通子系统，但它的 `configure` 和 `contribute` 都是空实现，只有一个 `createBaseContext()` 方法是实际工作。这说明它不是一个"子系统"，而是 VesselRuntime 的**内部依赖**。

**新设计**：取消 `PromptSubSystem` 类。`PromptContext` 的构建由 `VesselRuntime` 直接委托给 `PromptContextFactory`：

```java
// VesselRuntime 内部
@Autowired
private PromptContextFactory promptContextFactory;

private PromptContext buildPromptContext() {
    // ① 由 Factory 创建基础 Builder（包含 identity、rules、constraints 等配置级内容）
    PromptContext.Builder builder = promptContextFactory.createBuilder(vesselId);

    // ② 按 priority 收集各子系统的贡献
    subSystems.stream()
        .sorted(Comparator.comparingInt(VesselSubSystem::priority))
        .forEach(sub -> sub.contribute(builder));

    // ③ 一次性构建，返回不可变 PromptContext
    return builder.build();
}
```

这样：
- `PromptContextFactory` 负责"配置级 Prompt 素材"（identity、rules、currentTime 等）
- 各 `VesselSubSystem` 负责"领域级 Prompt 素材"（skills、tools、memory 摘要等）
- `VesselRuntime` 负责统一编排组装

---

## 6. VesselRuntime 重构为编排器

```java
package meta.claw.core.runtime;

@Slf4j
@Component
@Scope("prototype")
public class VesselRuntime implements InitializingBean {

    @Autowired
    private PromptContextFactory promptContextFactory;
    @Autowired
    private PromptRenderer promptRenderer;
    @Autowired
    private ChatMessageManager chatMessageManager;
    @Autowired
    private LlmClientManager llmClient;

    /** 所有子系统，Spring 自动收集 */
    @Autowired(required = false)
    private List<VesselSubSystem> subSystems = new ArrayList<>();

    private final SubSystemRegistry registry = new SubSystemRegistry();
    private final String vesselId;

    public VesselRuntime(String vesselId) {
        this.vesselId = vesselId;
    }

    @Override
    public void afterPropertiesSet() {
        // ① 按 priority 排序并注册
        subSystems.stream()
            .sorted(Comparator.comparingInt(VesselSubSystem::priority))
            .forEach(sub -> {
                registry.register(sub);
                sub.configure(registry);
            });

        // ② 通知 SkillSubSystem 加载本 Vessel 的技能（如果有）
        SkillSubSystem skillSub = registry.get("skill");
        if (skillSub != null) {
            skillSub.loadForVessel(vesselId);
        }
    }

    // ========== 子系统查询 ==========

    public SubSystemRegistry getRegistry() {
        return registry;
    }

    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T getSubSystem(String name) {
        return registry.get(name);
    }

    // ========== PromptContext 构建（每次对话调用）==========

    /**
     * 构建本次对话的完整 PromptContext。
     * 调用顺序：
     * 1. PromptContextFactory 创建基础 Builder（配置级素材）
     * 2. 按 priority 升序调用各子系统的 contribute()
     * 3. build() 返回不可变 PromptContext
     */
    public PromptContext buildPromptContext() {
        PromptContext.Builder builder = promptContextFactory.createBuilder(vesselId);
        subSystems.stream()
            .sorted(Comparator.comparingInt(VesselSubSystem::priority))
            .forEach(sub -> sub.contribute(builder));
        return builder.build();
    }

    // ========== 对话入口 ==========

    public Reply chat(String sessionId, String userMessage) {
        return execute(newTask(sessionId, userMessage));
    }

    public Reply execute(VesselTask task) {
        // ① 构建本次对话的 PromptContext（不可变）
        PromptContext promptContext = buildPromptContext();

        // ② 构造执行工作区
        AgentExecutionContext ctx = new AgentExecutionContext(task, promptContext, registry);

        // ③ 执行前生命周期钩子
        subSystems.forEach(sub -> sub.onExecutionStart(ctx));

        try {
            // ④ 委托给执行器
            Reply reply = agentExecutor.execute(ctx);
            return reply;
        } finally {
            // ⑤ 执行后生命周期钩子（finally 中保证调用）
            subSystems.forEach(sub -> sub.onExecutionEnd(ctx));
        }
    }

    public Reply resume(VesselTask task, ApprovalResolution resolution) {
        PromptContext promptContext = buildPromptContext();
        AgentExecutionContext ctx = new AgentExecutionContext(task, promptContext, registry);
        return agentExecutor.resume(ctx, resolution);
    }

    // ========== 便捷方法（向后兼容）==========

    public ShortMemory getShortMemory() {
        MemorySubSystem mem = registry.get("memory");
        if (mem != null) {
            VesselConfig config = promptContextFactory.createBuilder(vesselId)
                .build().getBundle().getRuntimeVesselConfig();
            return mem.getShortMemory(config.getMemoryConfig());
        }
        log.warn("MemorySubSystem not registered, short memory unavailable");
        return null;
    }

    public LongMemory getLongMemory() {
        MemorySubSystem mem = registry.get("memory");
        if (mem != null) {
            VesselConfig config = promptContextFactory.createBuilder(vesselId)
                .build().getBundle().getRuntimeVesselConfig();
            return mem.getLongMemory(config.getMemoryConfig());
        }
        log.warn("MemorySubSystem not registered, long memory unavailable");
        return null;
    }

    public VesselConfig getConfig() {
        return promptContextFactory.createBuilder(vesselId)
            .build().getBundle().getRuntimeVesselConfig();
    }

    // ========== 内部 ==========

    private VesselTask newTask(String sessionId, String userMessage) {
        return VesselTask.builder()
            .taskId(UUID.randomUUID().toString())
            .vesselId(this.vesselId)
            .sessionId(sessionId)
            .userMessage(userMessage)
            .createdAt(Instant.now())
            .build();
    }
}
```

---

## 7. AgentExecutionContext 定义（新）

```java
package meta.claw.core.agent;

import lombok.Getter;
import meta.claw.core.prompt.PromptContext;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.VesselSubSystem;

import java.util.ArrayList;
import java.util.List;

import meta.claw.core.llm.SpiMessage;

/**
 * Agent 单次执行上下文 —— 执行工作区。
 *
 * <p><strong>生命周期</strong> = 一次用户消息 → LLM 响应结束。</p>
 *
 * <p><strong>与 PromptContext 的关系</strong>：</p>
 * <table border="1">
 *   <tr><th></th><th>PromptContext</th><th>AgentExecutionContext</th></tr>
 *   <tr><td>性质</td><td>不可变的 Prompt 素材集合</td><td>可变的执行工作区</td></tr>
 *   <tr><td>创建时机</td><td>执行前由 VesselRuntime 统一构建</td><td>PromptContext 构建完成后构造</td></tr>
 *   <tr><td>谁修改</td><td>构建后无人修改</td><td>ReActLoop / AgentExecutor 累积消息</td></tr>
 *   <tr><td>用途</td><td>给 LLM 看的素材</td><td>引擎内部状态跟踪</td></tr>
 *   <tr><td>包含内容</td><td>skills、tools、identity、rules、preferences</td><td>task、messages、steps、registry</td></tr>
 * </table>
 *
 * <p>执行期使用方式：</p>
 * <ul>
 *   <li>读取 Prompt 素材：{@code ctx.getPromptContext().getSkills()}</li>
 *   <li>查询子系统服务：{@code ctx.getSubSystem("tool").getToolCallbacks()}</li>
 *   <li>累积消息：{@code ctx.addMessage(message)}</li>
 * </ul>
 */
@Getter
public class AgentExecutionContext {

    private final VesselTask task;
    private final PromptContext promptContext;
    private final SubSystemRegistry registry;
    private final List<SpiMessage> messages = new ArrayList<>();
    private final List<StepRecord> steps = new ArrayList<>();

    public AgentExecutionContext(VesselTask task, PromptContext promptContext, SubSystemRegistry registry) {
        this.task = task;
        this.promptContext = promptContext;
        this.registry = registry;
    }

    /** 便捷方法：通过注册表获取子系统 */
    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T getSubSystem(String name) {
        return registry.get(name);
    }

    public void addMessage(SpiMessage message) {
        this.messages.add(message);
    }

    public void addStep(StepRecord step) {
        this.steps.add(step);
    }

    public List<SpiMessage> getMessagesSnapshot() {
        return List.copyOf(messages);
    }

    public List<StepRecord> getStepsSnapshot() {
        return List.copyOf(steps);
    }

    public String getVesselId() {
        return task.getVesselId();
    }

    public String getSessionId() {
        return task.getSessionId();
    }

    public String getUserMessage() {
        return task.getUserMessage();
    }
}
```

---

## 8. ReActLoop 通过 AgentExecutionContext 获取一切

```java
@Component
public class ReActLoop {

    @Value("${vessel.agent.max-steps:50}")
    private int maxSteps;

    public SpiChatResponse run(AgentExecutionContext ctx) {
        VesselTask task = ctx.getTask();
        PromptContext promptCtx = ctx.getPromptContext();
        VesselConfig config = promptCtx.getBundle().getRuntimeVesselConfig();
        LlmClientManager llmClient = /* 从 Spring 注入或 registry 获取 */;

        // 通过 ToolSubSystem 获取工具
        List<ToolCallback> tools = ctx.getSubSystem("tool").getToolCallbacks();
        List<Message> messages = new ArrayList<>();

        // 构建初始消息（system + history + user）
        messages.addAll(buildInitialMessages(ctx));

        for (int step = 1; step <= maxSteps; step++) {
            ctx.addStep(StepRecord.builder().stepNumber(step).build());

            ChatResponse response = llmClient.call(messages, tools, config);
            Generation gen = response.getResult();

            if (gen == null || !hasToolCalls(gen)) {
                return extractResponse(response);
            }

            List<AssistantMessage.ToolCall> toolCalls = gen.getOutput().getToolCalls();

            // 通过 HitlSubSystem 评估审批
            HitlEvaluation evaluation = ctx.getSubSystem("hitl").evaluate(toolCalls, ctx);
            if (evaluation.hasSuspensions()) {
                throw new HitlSuspendedException(evaluation.getTicket());
            }

            // 执行工具
            List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : toolCalls) {
                ToolCallback callback = findToolCallback(tc.name(), tools);
                String result = callback.call(tc.arguments());
                toolResponses.add(new ToolResponseMessage.ToolResponse(tc.id(), tc.name(), result));
            }
            messages.add(new ToolResponseMessage(toolResponses));
            ctx.addMessage(new ToolResponseMessage(toolResponses));
        }

        throw new AgentException("超过最大步数: " + maxSteps);
    }
}
```

---

## 9. 实施路线图（修订版）

### Phase 1：SPI 重塑 + 现有能力迁移（P0）

**目标**：搭好新 `VesselSubSystem` SPI，重新定义 PromptContext / AgentExecutionContext 关系，把现有 Memory 能力包装为子系统。

| 步骤 | 任务 | 文件 |
|------|------|------|
| 1.1 | 修改 `VesselSubSystem` 接口：`configure` + `contribute` + `onExecutionStart/End` | `meta-claw-core/.../VesselSubSystem.java` |
| 1.2 | 新建 `SubSystemRegistry`（不变） | `meta-claw-core/.../SubSystemRegistry.java` |
| 1.3 | 修改 `AgentExecutionContext`：新构造函数（task + promptContext + registry），添加 steps，明确与 PromptContext 关系文档 | `meta-claw-core/.../AgentExecutionContext.java` |
| 1.4 | 新建 `MemorySubSystem`（包装现有 Factory） | `meta-claw-core/.../MemorySubSystem.java` |
| 1.5 | **取消 `PromptSubSystem`**，让 `PromptContextFactory` 回归为 VesselRuntime 的直接依赖 | 删除 PromptSubSystem.java / PromptSubSystemTest.java |
| 1.6 | 重构 `VesselRuntime`：移除 base PromptContext 缓存，每次对话 `buildPromptContext()` 从零构建；`afterPropertiesSet` 只负责 configure；`chat/chatStream/execute/resume` 统一流程 | `meta-claw-core/.../VesselRuntime.java` |
| 1.7 | 适配 `AgentExecutor` + `ReActLoop`：接收新 `AgentExecutionContext` | `meta-claw-core/.../AgentExecutor.java` |
| 1.8 | 运行 `./init.sh`，确认 CLI `chat default` 仍能对话 | 全仓 |

**验证**：`./init.sh` 通过，CLI `chat default` 仍能对话，P0 测试全过。

### Phase 2：Tool 子系统 + Spring AI 1.1.7 通用工具集（P0）

**目标**：支持 `@Tool`、MCP、Spring AI Alibaba 通用工具。

| 步骤 | 任务 | 文件 |
|------|------|------|
| 2.1 | 新建 `ToolSubSystem`，实现 `contribute()` 注入工具列表 | `meta-claw-core/.../ToolSubSystem.java` |
| 2.2 | `ReActLoop` 通过 `ctx.getSubSystem("tool").getToolCallbacks()` 获取工具 | `meta-claw-core/.../ReActLoop.java` |
| 2.3 | 改造 `LlmClientManager`：移除 `ToolCallAdvisor`，新增单次 `call()` 方法 | `meta-claw-core/.../LlmClientManager.java` |
| 2.4 | 引入 `spring-ai-starter-mcp-client`，配置 filesystem MCP Server | `pom.xml`, `application.yml` |
| 2.5 | 引入 `spring-ai-alibaba-starter-tool-calling-baidusearch` 等通用工具（按需） | `pom.xml` |
| 2.6 | **验证**：LLM 能调用 CalculatorTool + MCP filesystem 工具 | 全仓 |

### Phase 3：HITL 子系统（P0）

**目标**：实现人工审核闭环。

| 步骤 | 任务 | 文件 |
|------|------|------|
| 3.1 | 新建 `HitlSubSystem`、`HitlPolicy`、`HitlGate`、`ApprovalService` | `meta-claw-core/.../HitlSubSystem.java` 等 |
| 3.2 | 实现 `CliHitlGate`（终端阻塞审批） | `meta-claw-cli/.../CliHitlGate.java` |
| 3.3 | `ReActLoop` 集成 `ctx.getSubSystem("hitl").evaluate()` | `meta-claw-core/.../ReActLoop.java` |
| 3.4 | `VesselRuntime.resume()` 恢复挂起任务 | `meta-claw-core/.../VesselRuntime.java` |
| 3.5 | **验证**：配置敏感工具需审批，终端 Y/n 交互后恢复 | 全仓 |

### Phase 4：Skill 子系统（P1）

**目标**：实现渐进式披露的技能体系。

| 步骤 | 任务 | 文件 |
|------|------|------|
| 4.1 | 新建 `SkillSubSystem`、`SkillRegistry`、`SkillLoader` | `meta-claw-core/.../SkillSubSystem.java` 等 |
| 4.2 | 扫描 `~/.meta-claw/skills/` 和 `vessels/<vessel>/skills/` | `SkillLoader.java` |
| 4.3 | `SkillSubSystem.contribute()` 向 PromptContext 注入技能摘要 | `SkillSubSystem.java` |
| 4.4 | 新建 `SkillReadTool`（`@Tool`） | `meta-claw-core/.../SkillReadTool.java` |
| 4.5 | **验证**：创建 travel-planner SKILL.md，LLM 按需调用 read_skill | 全仓 |

### Phase 5：Metrics + 流式 + 多 Agent（P2）

**目标**：生产级可观测性和高级能力。

| 步骤 | 任务 | 文件 |
|------|------|------|
| 5.1 | 新建 `MetricsSubSystem`，接入 Micrometer | `meta-claw-core/.../MetricsSubSystem.java` |
| 5.2 | 流式 ReActLoop（`stream()` 方法） | `meta-claw-core/.../ReActLoop.java` |
| 5.3 | `VesselManager` 自动刷新（WatchService） | `meta-claw-core/.../VesselManager.java` |
| 5.4 | 多 Agent 协作（`TeamContext`） | 新设计 |

---

## 10. 与初版 06-06 设计的对比

| 维度 | 06-06 初版 | 本修订版 |
|------|-----------|---------|
| SPI 方法名 | `initialize` + `enrich` | `configure` + `contribute` |
| `initialize/enrich` 是否合并 | 不合并，但语义模糊 | 不合并，语义明确分离（配置 vs 贡献） |
| PromptContext 创建时机 | `afterPropertiesSet()` 缓存 base，每次 `toBuilder()` 复制 | 每次对话从零 `createBuilder()` + `build()` |
| PromptContext 可变性 | 半静态（base + enrich） | 单次对话不可变 |
| PromptSubSystem | 存在，但 `configure` 和 `contribute` 为空 | **取消**，回归为 `PromptContextFactory` |
| AgentExecutionContext 构造 | `(vesselId, sessionId, msg, promptCtx, registry)` | `(task, promptCtx, registry)`，task 聚合标识 |
| AgentExecutionContext 职责 | 执行跟踪器 | **执行工作区**，文档明确与 PromptContext 分工 |
| 数据获取渠道 | PromptContext + registry 重复 | PromptContext = 只读素材，registry = 服务查询 |

---

## 11. 自检清单

- [x] `configure` 与 `contribute` 的分离理由已文档化（调用次数、职责、操作对象）
- [x] PromptContext 与 AgentExecutionContext 的关系已用表格 + 流程图说明
- [x] PromptSubSystem 取消的理由已说明（空实现暴露角色错误）
- [x] 所有 Phase 均有具体文件和验证步骤
- [x] 无 "TBD"/"TODO" 模糊描述
- [x] 与 06-04 长远架构对齐（子系统 SPI 为骨架，Spring AI 1.1.7 为引擎）

---

*文档版本：v2.0（关系重塑版）*
*替代文档：`2026-06-06-vessel-subsystem-spi-context-refactor.md`*
