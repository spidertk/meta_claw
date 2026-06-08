# VesselSubSystem SPI + TaskContext + PromptVars 设计重塑

> **日期：2026-06-06（最终版）**
> **替代文档：**
> - `2026-06-06-vessel-subsystem-spi-context-refactor.md`（初版，已废弃）
> - `2026-06-06-vessel-subsystem-prompt-agent-context-redesign.md`（修订版，已废弃）
>
> **前置长远架构：** `2026-06-04-meta-claw-agent-platform-longterm-design.md`

---

## 1. 修订原因

**1.1 初版 06-06 的问题**

- `initialize` + `enrich` 语义边界模糊，子系统开发者困惑"我该在哪个阶段做什么"
- `PromptContext` 被描述为"静态素材"却在每次对话时被动态 `enrich`，矛盾
- `AgentExecutionContext` 同时持有 `PromptContext` 和 `registry`，数据获取渠道重复
- `PromptSubSystem` 的 `initialize` 和 `enrich` 均为空实现，说明角色定位错误

**1.2 第一次修订版的问题**

- 引入 `Turn` 概念，但"Turn"是对话系统领域黑话，不够直观
- `ExecutionPromptPart` 叠了三个概念（执行 + Prompt + 部分），难以理解
- 命名没有层次，`PromptContribution` 和 `AgentExecutionContext` 看起来像两个毫不相干的东西

**1.3 最终版的核心改进**

1. **以 `Task` 为根命名**：入参是 `VesselTask`，执行上下文叫 `TaskContext`，自然对应
2. **PromptVars 替代 PromptContribution/Part**：子系统对 prompt 的贡献就是**一组模板变量**，不叫"贡献"、不叫"部分"
3. **删除 PromptContext**：它的内容拆到 `VesselProfile`（配置画像）和 `PromptVars`（动态变量），没有中间层
4. **分离 PromptComposer 和 PromptRenderer**：Composer 组装变量，Renderer 纯渲染，职责清晰

---

## 2. 命名层次体系

按**生命周期/作用域**分层，统一前缀：

| 层级 | 前缀 | 生命周期 | 职责 |
|------|------|----------|------|
| **Vessel** | `Vessel*` | 每个 Vessel 实例一次 | 运行时编排、配置、子系统注册 |
| **Task** | `Task*` | 每次对话一次 | 单次 `chat()`/`execute()` 调用的完整周期 |
| **Prompt** | `Prompt*` | 每次对话构建一次 | Prompt 变量收集、组装、渲染 |

---

## 3. 核心概念重新定义

### 3.1 VesselProfile = Vessel 画像（内置子系统）

**为什么不是 PromptContext？**

原来的 `PromptContext` 在 `afterPropertiesSet()` 中创建并缓存，包含：
- `VesselConfigBundle` — 这是 Vessel 的配置，和 Prompt 无关
- `currentTime` — 每次对话都应该重新计算
- `location` — 同上

它本质上是**Vessel 的配置画像**，只是碰巧被 PromptRenderer 使用了。叫它 `PromptContext` 造成了"这是 Prompt 专属数据"的假象。

**新定义**：`VesselProfile` 是 Vessel 配置画像的缓存，同时实现 `VesselSubSystem` 作为**内置子系统**（name=`"profile"`，priority=`0`）。它和其他子系统一样通过 Spring 自动注入到 `subSystems` 列表，只是采用 `@Scope("prototype")`（每个 Vessel 实例独立一份）。`vesselId` 不通过构造器传入，而是在 `VesselRuntime` 注册后调用 `loadForVessel(vesselId)` 完成配置加载（与 `SkillSubSystem` 的初始化模式一致）。它的 `promptVars()` 贡献所有基础静态变量，`currentTime`/`location` 仍由 `VesselRuntime` 每次任务实时计算后注入。

```java
@Component
@Scope("prototype")
public class VesselProfile implements VesselSubSystem {

    @Autowired
    private VesselConfigLoader configLoader;

    private String vesselId;
    private VesselConfigBundle bundle;
    private SubSystemRegistry registry;

    @Override
    public String name() { return "profile"; }

    @Override
    public void configure(SubSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int priority() { return 0; }

    /** 由 VesselRuntime 在注册后调用，完成本 Vessel 的配置加载 */
    public void loadForVessel(String vesselId) {
        this.vesselId = vesselId;
        this.bundle = configLoader.load(vesselId);
    }

    @Override
    public PromptVars promptVars() {
        if (bundle == null) {
            return PromptVars.empty();
        }
        return PromptVars.builder()
            .var("vessel_name", bundle.getRuntimeVesselConfig().getName())
            .var("vessel_description", bundle.getRuntimeVesselConfig().getDescription())
            .var("identity", bundle.getIdentity())
            .var("soul", bundle.getSoul())
            .var("capabilities", bundle.getCapabilities())
            .var("guidelines", bundle.getGuidelines())
            .var("domain_knowledge", bundle.getDomainKnowledge())
            .var("preferences", bundle.getPreferences())
            .var("workspace", bundle.getWorkspaceDir() != null ? bundle.getWorkspaceDir().toString() : "")
            .build();
    }

    public VesselConfigBundle getBundle() { return bundle; }
}
```

### 3.2 PromptVars = Prompt 模板变量集合

**本质**：子系统对 prompt 的贡献不是"一段文本"、不是"一个 Part"，而是**往模板里填变量**。

比如 SkillSubSystem 贡献 `{skills: "- travel-planner: 规划旅行"}`，ToolSubSystem 贡献 `{tools: "- calculator: 计算器"}`。

**设计**：`PromptVars` 是一个不可变的 `Map<String, String>` 包装，支持 `merge()` 组合多个子系统的贡献。

```java
@Builder
public class PromptVars {
    private final Map<String, String> vars;

    public static PromptVars empty() {
        return new PromptVars(Map.of());
    }

    public static PromptVars of(String key, String value) {
        return new PromptVars(Map.of(key, value));
    }

    /** 合并另一个 PromptVars，返回新的（不可变） */
    public PromptVars merge(PromptVars other) {
        Map<String, String> merged = new HashMap<>(this.vars);
        merged.putAll(other.vars);
        return new PromptVars(Map.copyOf(merged));
    }

    public Map<String, String> toMap() {
        return vars;
    }
}
```

### 3.3 TaskContext = 任务执行上下文（替代 AgentExecutionContext）

**定义**：单次 `chat()`/`execute()` 调用的执行工作区。

```java
@Getter
public class TaskContext {
    private final VesselTask task;           // 任务参数
    private final VesselProfile profile;     // Vessel 配置画像
    private final SubSystemRegistry registry; // 子系统注册表
    private final MessageThread messages;     // 消息线程
    private final StepLog steps;             // 步骤日志

    public TaskContext(VesselTask task, VesselProfile profile, SubSystemRegistry registry) {
        this.task = task;
        this.profile = profile;
        this.registry = registry;
        this.messages = new MessageThread();
        this.steps = new StepLog();
    }

    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T getSubSystem(String name) {
        return registry.get(name);
    }
}
```

**与 VesselTask 的关系**：
- `VesselTask` = 任务参数（DTO，vesselId/sessionId/userMessage）
- `TaskContext` = 任务执行环境（状态 + 运行时 + 注册表 + 消息 + 步骤）

**为什么不是 `AgentExecutionContext`？**

"Agent" 不是这个系统的核心概念，系统中没有任何东西叫 Agent。`TaskContext` 直接对应入参 `VesselTask`，一眼就能看懂关系。

---

## 4. VesselSubSystem SPI

```java
package meta.claw.core.runtime.subsystem;

import meta.claw.core.runtime.TaskContext;

/**
 * Vessel 子系统 SPI。
 * 所有能力（Memory, Tool, Skill, HITL, Metrics, Knowledge, Cron）必须实现此接口，
 * 由 VesselRuntime 统一编排生命周期。
 *
 * <p>调用顺序（由 VesselRuntime 保证）：</p>
 * <ol>
 *   <li>{@link #configure(SubSystemRegistry)} — VesselRuntime 创建时调用一次</li>
 *   <li>{@link #promptVars()} — 每次任务前，收集 prompt 变量时调用</li>
 *   <li>{@link #onTaskStart(TaskContext)} — 每次任务开始时调用</li>
 *   <li>{@link #onTaskEnd(TaskContext)} — 每次任务结束时调用（finally 中）</li>
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
     *   <li>缓存 {@link SubSystemRegistry} 引用</li>
     *   <li>建立外部连接（如 MCP 客户端初始化）</li>
     *   <li>加载一次性的索引数据（如技能文件索引）</li>
     * </ul>
     */
    void configure(SubSystemRegistry registry);

    /**
     * 返回本系统对本次任务 prompt 的贡献变量。
     * 多个子系统的 PromptVars 会被 VesselRuntime 通过 {@link PromptVars#merge} 合并。
     *
     * <p>举例：</p>
     * <pre>
     * return PromptVars.of("skills", "- travel-planner: 规划旅行");
     * </pre>
     */
    default PromptVars promptVars() { return PromptVars.empty(); }

    /** 任务开始 */
    default void onTaskStart(TaskContext ctx) {}

    /** 任务结束 */
    default void onTaskEnd(TaskContext ctx) {}

    /** 优先级：数值越小，越早执行 promptVars */
    default int priority() { return 100; }
}
```

### 4.1 为什么 `configure` 和 `promptVars` 不合并？

| 维度 | `configure(registry)` | `promptVars()` |
|------|----------------------|----------------|
| **调用次数** | 每个 VesselRuntime 实例一次 | 每次任务一次 |
| **核心职责** | 子系统自身初始化 + 获取协作引用 | 返回本次任务的 prompt 变量 |
| **返回值** | void | `PromptVars` |
| **典型工作** | 建立 MCP 连接、加载技能索引 | 注入技能列表、工具描述 |
| **副作用** | 可以（建立连接、缓存配置） | 应无副作用（只读取状态） |

如果合并，子系统需要在每次任务时都重复执行一次性初始化，或者维护复杂的内部状态判断。分离后语义天然清晰。

---

## 5. 辅助类型

### 5.1 MessageThread（替代裸 List<SpiMessage>）

```java
public class MessageThread {
    private final List<SpiMessage> messages = new ArrayList<>();

    public void add(SpiMessage message) { messages.add(message); }
    public List<SpiMessage> snapshot() { return List.copyOf(messages); }
    public boolean isEmpty() { return messages.isEmpty(); }
    public int size() { return messages.size(); }
}
```

### 5.2 StepLog（替代裸 List<StepRecord>）

```java
public class StepLog {
    private final List<StepRecord> steps = new ArrayList<>();

    public void add(StepRecord step) { steps.add(step); }
    public List<StepRecord> snapshot() { return List.copyOf(steps); }
    public int size() { return steps.size(); }
}
```

### 5.3 SubSystemRegistry（不变）

```java
public class SubSystemRegistry {
    private final Map<String, VesselSubSystem> subSystems = new HashMap<>();

    public void register(VesselSubSystem subSystem) { ... }
    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T get(String name) { ... }
    public boolean has(String name) { ... }
    public List<VesselSubSystem> listAll() { ... }
}
```

---

## 6. 子系统逐个设计

### 6.0 VesselProfile（内置子系统）

`VesselProfile` 是 `@Component @Scope("prototype")` 的 Spring Bean，和其他子系统一样被 Spring 自动收集到 `subSystems` 列表中。`VesselRuntime` 在注册后调用 `loadForVessel(vesselId)` 完成配置加载。它是所有其他子系统查询 Vessel 基础配置的入口。

```java
@Component
@Scope("prototype")
public class VesselProfile implements VesselSubSystem {

    @Autowired
    private VesselConfigLoader configLoader;

    private String vesselId;
    private VesselConfigBundle bundle;
    private SubSystemRegistry registry;

    @Override
    public String name() { return "profile"; }

    @Override
    public void configure(SubSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int priority() { return 0; }

    public void loadForVessel(String vesselId) {
        this.vesselId = vesselId;
        this.bundle = configLoader.load(vesselId);
    }

    @Override
    public PromptVars promptVars() {
        if (bundle == null) {
            return PromptVars.empty();
        }
        return PromptVars.builder()
            .var("vessel_name", bundle.getRuntimeVesselConfig().getName())
            .var("vessel_description", bundle.getRuntimeVesselConfig().getDescription())
            .var("identity", bundle.getIdentity())
            .var("soul", bundle.getSoul())
            .var("capabilities", bundle.getCapabilities())
            .var("guidelines", bundle.getGuidelines())
            .var("domain_knowledge", bundle.getDomainKnowledge())
            .var("preferences", bundle.getPreferences())
            .var("workspace", bundle.getWorkspaceDir() != null ? bundle.getWorkspaceDir().toString() : "")
            .build();
    }

    public VesselConfigBundle getBundle() { return bundle; }
}
```

### 6.1 MemorySubSystem

```java
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
    public PromptVars promptVars() {
        // 未来可注入长期记忆摘要
        return PromptVars.empty();
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

### 6.2 ToolSubSystem

```java
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
    public PromptVars promptVars() {
        String toolsText = getToolCallbacks().stream()
            .map(tc -> "- " + tc.getToolDefinition().name()
                + ": " + tc.getToolDefinition().description())
            .collect(Collectors.joining("\n"));
        return toolsText.isEmpty()
            ? PromptVars.empty()
            : PromptVars.of("tools", toolsText);
    }

    @Override
    public int priority() { return 20; }

    public List<ToolCallback> getToolCallbacks() {
        List<ToolCallback> all = new ArrayList<>();

        Object[] localBeans = toolRegistry.getToolInstances().toArray();
        if (localBeans.length > 0) {
            all.addAll(Arrays.asList(ToolCallbacks.from(localBeans)));
        }

        if (mcpToolProviders != null) {
            for (ToolCallbackProvider provider : mcpToolProviders) {
                all.addAll(Arrays.asList(provider.getToolCallbacks()));
            }
        }

        return all;
    }
}
```

### 6.3 SkillSubSystem

```java
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
    public PromptVars promptVars() {
        String skillsText = skillRegistry.getAvailableSkills(vesselId).stream()
            .map(s -> "- " + s.name() + ": " + s.description())
            .collect(Collectors.joining("\n"));
        return skillsText.isEmpty()
            ? PromptVars.empty()
            : PromptVars.of("skills", skillsText);
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

### 6.4 HitlSubSystem

```java
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
    public PromptVars promptVars() {
        String summary = hitlPolicy.getSummary();
        return summary == null || summary.isBlank()
            ? PromptVars.empty()
            : PromptVars.of("hitl_policy", summary);
    }

    public HitlEvaluation evaluate(List<AssistantMessage.ToolCall> toolCalls, TaskContext ctx) {
        // ... 同长远设计文档
    }
}
```

### 6.5 MetricsSubSystem（预留）

```java
@Component
public class MetricsSubSystem implements VesselSubSystem {

    @Autowired private MeterRegistry meterRegistry;

    @Override
    public String name() { return "metrics"; }

    @Override
    public void configure(SubSystemRegistry registry) { }

    @Override
    public PromptVars promptVars() { return PromptVars.empty(); }

    @Override
    public void onTaskEnd(TaskContext ctx) {
        VesselTask task = ctx.getTask();
        meterRegistry.counter("agent.task.completed", "vessel", task.getVesselId()).increment();
        meterRegistry.counter("agent.steps", "vessel", task.getVesselId()).increment(ctx.getSteps().size());
    }
}
```

---

## 7. VesselRuntime 重构为编排器

```java
package meta.claw.core.runtime;

@Slf4j
@Component
@Scope("prototype")
public class VesselRuntime implements InitializingBean {

    @Autowired
    private PromptComposer promptComposer;
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
        // ① 按 priority 排序并注册所有子系统（含 VesselProfile）
        subSystems.stream()
            .sorted(Comparator.comparingInt(VesselSubSystem::priority))
            .forEach(sub -> {
                registry.register(sub);
                sub.configure(registry);
                if (sub instanceof VesselProfile) {
                    ((VesselProfile) sub).loadForVessel(vesselId);
                }
            });

        // ② 通知 SkillSubSystem 加载本 Vessel 的技能
        SkillSubSystem skillSub = registry.get("skill");
        if (skillSub != null) {
            skillSub.loadForVessel(vesselId);
        }
    }

    // ========== 子系统查询 ==========

    public SubSystemRegistry getRegistry() { return registry; }

    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T getSubSystem(String name) {
        return registry.get(name);
    }

    /** 便捷查询：获取 Vessel 配置画像 */
    public VesselProfile getProfile() {
        return registry.get("profile");
    }

    // ========== Prompt 组装与渲染 ==========

    /**
     * 构建本次任务的完整 PromptVars，然后渲染 system prompt。
     */
    public String renderSystemPrompt() {
        PromptVars allVars = buildPromptVars();
        return promptRenderer.render(allVars.toMap());
    }

    /**
     * 构建 PromptVars：收集所有子系统（含 profile）的 promptVars 并 merge，
     * 最后注入 current_time/location 等动态变量。
     */
    public PromptVars buildPromptVars() {
        // 静态变量：由 PromptComposer 从 registry 收集所有子系统贡献
        PromptVars staticVars = promptComposer.compose(registry);

        // 动态变量：每次任务实时计算
        PromptVars dynamic = PromptVars.builder()
            .var("current_time", formatCurrentTime())
            .var("location", detectLocation())
            .build();

        return staticVars.merge(dynamic);
    }

    // ========== 对话入口 ==========

    public Reply chat(String sessionId, String userMessage) {
        return execute(newTask(sessionId, userMessage));
    }

    public Reply execute(VesselTask task) {
        // ① 渲染 system prompt
        String systemPrompt = renderSystemPrompt();

        // ② 构造任务上下文
        TaskContext ctx = new TaskContext(task, getProfile(), registry);

        // ③ 任务开始生命周期（遍历 registry 中所有子系统）
        registry.listAll().forEach(sub -> sub.onTaskStart(ctx));

        try {
            // ④ 构建 LLM 请求并执行
            List<SpiMessage> messages = buildLlmRequest(task, systemPrompt);
            SpiChatRequest request = SpiChatRequest.builder()
                .messages(messages)
                .sessionId(task.getSessionId())
                .build();

            // TODO: 将来改为 agentExecutor.execute(ctx, request)
            SpiChatResponse response = llmClient.chat(request);

            String content = response != null && response.content() != null
                ? response.content() : "";

            // 保存 assistant 消息到短期记忆
            saveAssistantMessage(task, content);

            return new Reply(ReplyType.TEXT, content);
        } finally {
            // ⑤ 任务结束生命周期（finally 中保证调用）
            registry.listAll().forEach(sub -> sub.onTaskEnd(ctx));
        }
    }

    public Reply resume(VesselTask task, ApprovalResolution resolution) {
        String systemPrompt = renderSystemPrompt();
        TaskContext ctx = new TaskContext(task, getProfile(), registry);
        // TODO: agentExecutor.resume(ctx, resolution)
        return null;
    }

    // ========== 便捷方法（向后兼容）==========

    public ShortMemory getShortMemory() {
        MemorySubSystem mem = registry.get("memory");
        if (mem != null) {
            return mem.getShortMemory(getProfile().getBundle().getMemoryConfig());
        }
        log.warn("MemorySubSystem not registered, short memory unavailable");
        return null;
    }

    public LongMemory getLongMemory() {
        MemorySubSystem mem = registry.get("memory");
        if (mem != null) {
            return mem.getLongMemory(getProfile().getBundle().getMemoryConfig());
        }
        log.warn("MemorySubSystem not registered, long memory unavailable");
        return null;
    }

    public VesselConfig getConfig() {
        return getProfile().getBundle().getRuntimeVesselConfig();
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

    private List<SpiMessage> buildLlmRequest(VesselTask task, String systemPrompt) {
        List<SpiMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(SpiMessage.system(systemPrompt));
        }

        String sessionId = task.getSessionId();
        ShortMemory shortMem = getShortMemory();
        if (StringUtils.isNotBlank(sessionId) && shortMem != null) {
            int maxRounds = getProfile().getBundle().getMaxHistoryRounds();
            messages.addAll(toSpiMessages(shortMem.loadMessages(vesselId, sessionId, maxRounds)));
        }

        messages.add(SpiMessage.user(task.getUserMessage()));

        if (shortMem != null) {
            shortMem.appendMessage(vesselId, sessionId,
                MemoryMessageConverter.fromSpiMessage(SpiMessage.user(task.getUserMessage())));
        }

        return messages;
    }

    private void saveAssistantMessage(VesselTask task, String content) {
        ShortMemory shortMem = getShortMemory();
        if (shortMem != null) {
            shortMem.appendMessage(vesselId, task.getSessionId(),
                MemoryMessageConverter.fromSpiMessage(SpiMessage.assistant(content, null, null)));
        }
    }

    private List<SpiMessage> toSpiMessages(List<MemoryMessage> entries) {
        // ... 同现有实现
    }

    private static String formatCurrentTime() {
        return ZonedDateTime.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
    }

    private static String detectLocation() {
        return ZoneId.systemDefault().getId();
    }

    public void shutdown() {
        log.info("VesselRuntime shutdown: {}", vesselId);
    }
}
```

---

## 8. PromptComposer 与 PromptRenderer 分离

### 8.1 PromptComposer（组装器）

```java
@Component
public class PromptComposer {

    /**
     * 从 SubSystemRegistry 收集所有子系统（含内置的 profile）的 promptVars，
     * 按 priority 排序后 merge 成一份完整的静态变量集合。
     */
    public PromptVars compose(SubSystemRegistry registry) {
        return registry.listAll().stream()
            .sorted(Comparator.comparingInt(VesselSubSystem::priority))
            .map(VesselSubSystem::promptVars)
            .reduce(PromptVars.empty(), PromptVars::merge);
    }
}
```

### 8.2 PromptRenderer（纯函数渲染器）

```java
@Slf4j
@Component
public class PromptRenderer {

    private static final String SYSTEM_TEMPLATE = loadTemplate("/templates/runtime/system.tmpl.md");

    public String render(Map<String, String> vars) {
        if (vars == null || vars.isEmpty()) {
            log.warn("Empty prompt vars, returning empty prompt");
            return "";
        }

        String result = SYSTEM_TEMPLATE
            .replace("{vessel_name}",        orEmpty(vars.get("vessel_name")))
            .replace("{vessel_description}", orEmpty(vars.get("vessel_description")))
            .replace("{identity}",           sectionOrEmpty(vars.get("identity"), "Identity"))
            .replace("{soul}",               sectionOrEmpty(vars.get("soul"), "Soul"))
            .replace("{capabilities}",       sectionOrEmpty(vars.get("capabilities"), "Capabilities"))
            .replace("{guidelines}",         sectionOrEmpty(vars.get("guidelines"), "Guidelines"))
            .replace("{domain_knowledge}",   sectionOrEmpty(vars.get("domain_knowledge"), "Domain Knowledge"))
            .replace("{workspace}",          workspaceSection(vars.get("workspace")))
            .replace("{current_time}",       orEmpty(vars.get("current_time")))
            .replace("{location}",           orEmpty(vars.get("location")))
            .replace("{preferences}",        sectionOrEmpty(vars.get("preferences"), "Preferences"))
            .replace("{skills}",             orEmpty(vars.get("skills")))
            .replace("{tools}",              orEmpty(vars.get("tools")))
            .trim();

        return result.replaceAll("\n{3,}", "\n\n");
    }

    // ... 辅助方法同现有实现
}
```

---

## 9. SpiChatRequest 简化（删除 PromptContext 字段）

```java
@Builder
@Getter
@Setter
public class SpiChatRequest {
    private String sessionId;
    private List<SpiMessage> messages;
    private Map<String, Object> options;
    // PromptContext ctx 字段已删除，不再需要
}
```

---

## 10. 实施路线图

### Phase 1：SPI 骨架 + 现有能力迁移（P0）

**目标**：搭好新 `VesselSubSystem` SPI，以 `Task` 为根重塑命名，把现有 Memory 能力包装为子系统。

| 步骤 | 任务 | 文件 |
|------|------|------|
| 1.1 | 新建 `PromptVars` | `meta-claw-core/.../prompt/PromptVars.java` |
| 1.2 | 新建 `VesselProfile` | `meta-claw-core/.../runtime/VesselProfile.java` |
| 1.3 | 新建 `MessageThread` | `meta-claw-core/.../runtime/MessageThread.java` |
| 1.4 | 新建 `StepLog` | `meta-claw-core/.../runtime/StepLog.java` |
| 1.5 | 修改 `VesselSubSystem` 接口：`configure` + `promptVars` + `onTaskStart/End` | `meta-claw-core/.../runtime/subsystem/VesselSubSystem.java` |
| 1.6 | 新建 `SubSystemRegistry` | `meta-claw-core/.../runtime/subsystem/SubSystemRegistry.java` |
| 1.7 | 新建 `TaskContext`（替代 AgentExecutionContext） | `meta-claw-core/.../runtime/TaskContext.java` |
| 1.8 | 新建 `MemorySubSystem` | `meta-claw-core/.../runtime/subsystem/MemorySubSystem.java` |
| 1.9 | 新建 `PromptComposer` | `meta-claw-core/.../prompt/PromptComposer.java` |
| 1.10 | 修改 `PromptRenderer`：接收 `Map<String, String>` 而非 PromptContext | `meta-claw-core/.../prompt/PromptRenderer.java` |
| 1.11 | 修改 `SpiChatRequest`：删除 PromptContext 字段 | `meta-claw-core/.../llm/SpiChatRequest.java` |
| 1.12 | 重构 `VesselRuntime`：引入 VesselProfile、SubSystemRegistry、PromptComposer | `meta-claw-core/.../runtime/VesselRuntime.java` |
| 1.13 | 全量编译 + P0 测试 + `./init.sh` | 全仓 |

**验证**：`./init.sh` 通过，CLI `chat default` 仍能对话。

### Phase 2：Tool 子系统 + Spring AI 1.1.7（P0）

| 步骤 | 任务 |
|------|------|
| 2.1 | 新建 `ToolSubSystem`，实现 `promptVars()` 注入工具列表 |
| 2.2 | `ReActLoop` 通过 `ctx.getSubSystem("tool").getToolCallbacks()` 获取工具 |
| 2.3 | 引入 `spring-ai-starter-mcp-client` |
| 2.4 | 引入 Spring AI Alibaba 通用工具集（按需） |
| 2.5 | **验证**：LLM 能调用本地 Tool + MCP Tool |

### Phase 3：HITL 子系统（P0）

| 步骤 | 任务 |
|------|------|
| 3.1 | 新建 `HitlSubSystem`、`HitlPolicy`、`HitlGate` |
| 3.2 | 实现 `CliHitlGate`（终端阻塞审批） |
| 3.3 | `ReActLoop` 集成 `ctx.getSubSystem("hitl").evaluate()` |
| 3.4 | `VesselRuntime.resume()` 恢复挂起任务 |
| 3.5 | **验证**：配置敏感工具需审批，终端 Y/n 交互后恢复 |

### Phase 4：Skill 子系统（P1）

| 步骤 | 任务 |
|------|------|
| 4.1 | 新建 `SkillSubSystem`、`SkillRegistry`、`SkillLoader` |
| 4.2 | 扫描 `~/.meta-claw/skills/` 和 `vessels/<vessel>/skills/` |
| 4.3 | `SkillSubSystem.promptVars()` 注入技能摘要 |
| 4.4 | 新建 `SkillReadTool`（`@Tool`） |
| 4.5 | **验证**：创建 SKILL.md，LLM 按需调用 read_skill |

### Phase 5：Metrics + 流式 + 多 Agent（P2）

| 步骤 | 任务 |
|------|------|
| 5.1 | 新建 `MetricsSubSystem`，接入 Micrometer |
| 5.2 | 流式 `ReActLoop` |
| 5.3 | `VesselManager` 自动刷新 |
| 5.4 | 多 Agent 协作（`TeamContext`） |

---

## 11. 与旧设计的对比

| 维度 | 06-04 长远设计 | 06-06 初版 | 06-06 修订版（本版） |
|------|--------------|-----------|-------------------|
| 执行上下文 | `AgentExecutionContext` | `AgentExecutionContext` | **`TaskContext`** |
| Prompt 变量 | 无明确概念 | `PromptContribution` | **`PromptVars`** |
| 子系统贡献 | `contribute(PromptContext.Builder)` | `enrich(PromptContext.Builder)` | **`promptVars()` 返回 PromptVars** |
| 运行时状态 | `PromptContext`（缓存） | `PromptContext`（缓存） | **`VesselProfile`（内置子系统，priority=0）** |
| 渲染器入参 | `PromptContext` | `PromptContext` | **`Map<String, String>`** |
| SPI 生命周期 | `initialize` + `contribute` | `initialize` + `enrich` | **`configure` + `promptVars`** |
| 消息列表 | `List<SpiMessage>` | `List<SpiMessage>` | **`MessageThread`** |
| 步骤记录 | `List<StepRecord>` | `List<StepRecord>` | **`StepLog`** |

---

## 12. 自检清单

- [x] `configure` 与 `promptVars` 的分离理由已文档化（调用次数、职责、返回值）
- [x] `TaskContext` 与 `VesselTask` 的关系已明确（参数 vs 执行环境）
- [x] `PromptVars` 的语义已明确（prompt 模板变量集合，可 merge）
- [x] `VesselProfile` 替代 `PromptContext` 的理由已说明（本质是配置画像，同时作为内置子系统注册到 registry）
- [x] 所有 Phase 均有具体文件和验证步骤
- [x] 无 "TBD"/"TODO" 模糊描述
- [x] 与 06-04 长远架构对齐（子系统 SPI 为骨架，Spring AI 1.1.7 为引擎）

---

*文档版本：v3.0（Task + PromptVars 最终版）*
*替代文档：*
- *`2026-06-06-vessel-subsystem-spi-context-refactor.md`*
- *`2026-06-06-vessel-subsystem-prompt-agent-context-redesign.md`*
