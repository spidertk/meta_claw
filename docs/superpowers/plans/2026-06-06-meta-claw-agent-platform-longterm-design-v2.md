# Meta-Claw Agent 平台长远设计 v2（基于 06-06 修正版）

> 日期：2026-06-06
> 原则：**子系统 SPI 是骨架，Spring AI 1.1.7 是引擎，VesselProfile 是配置画像，PromptVars 是 Prompt 组装协议。**
> 替代文档：`2026-06-04-meta-claw-agent-platform-longterm-design.md`（已废弃，存在设计偏差）

---

## 1. 设计原则（约束条件）

1. **VesselSubSystem SPI 必须存在**：所有能力（Memory、Tool、Skill、HITL、Metrics、Knowledge、Cron）必须作为子系统接入，不得直接硬编码在 `VesselRuntime` 中。
2. **Spring AI 1.1.7 原生能力最大化复用**：`@Tool`、MCP、`ToolCallback`、`ChatClient` 等框架能力优先复用，不重复造轮子。
3. **PromptVars 是子系统对 Prompt 的唯一贡献协议**：子系统不直接操作 Prompt 文本，只返回 `Map<String, String>` 形式的模板变量，由 `PromptComposer` 统一 merge。
4. **渐进式实现**：先搭 SPI 骨架，再填子系统血肉。每阶段结束必须能跑通 `./init.sh`。

---

## 2. 命名层次体系

按**生命周期/作用域**分层，统一前缀：

| 层级 | 前缀 | 生命周期 | 职责 |
|------|------|----------|------|
| **Vessel** | `Vessel*` | 每个 Vessel 实例一次 | 运行时编排、配置画像、子系统注册 |
| **Task** | `Task*` | 每次对话一次 | 单次 `chat()`/`execute()` 调用的完整周期 |
| **Prompt** | `Prompt*` | 每次对话构建一次 | Prompt 变量收集、组装、渲染 |

---

## 3. 核心概念重新定义

### 3.1 VesselProfile = Vessel 画像（内置子系统）

`VesselProfile` 是 `@Component @Scope("prototype")` 的 Spring Bean，和其他子系统一样被 Spring 自动收集到 `subSystems` 列表中。`VesselRuntime` 在注册后调用 `loadForVessel(vesselId)` 完成配置加载。它是所有其他子系统查询 Vessel 基础配置的入口，同时通过 `promptVars()` 贡献所有基础静态变量。

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

### 3.3 TaskContext = 任务执行上下文

**定义**：单次 `chat()`/`execute()` 调用的执行工作区。

```java
@Getter
public class TaskContext {
    private final VesselTask task;           // 任务参数
    private final VesselProfile profile;     // Vessel 配置画像（通过 registry 查询）
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

---

## 4. 总体架构：子系统编排模式

```text
Channel (CLI / Gateway / Weixin)
    ↓ EventBus
AgentLoop
    ├── VesselManager（路由、获取 Runtime）
    └── VesselRuntime.chat() / .execute()
            │
            ▼
    ┌─────────────────────────────────────────────────────────────┐
    │  VesselRuntime（子系统编排器）                               │
    │  - 持有 SubSystemRegistry                                   │
    │  - 所有子系统（含 VesselProfile）统一按 priority 注册       │
    │  - 生命周期：configure() → onTaskStart() → delegate         │
    │            → onTaskEnd()                                    │
    └─────────────────────────────────────────────────────────────┘
            │
            ▼
    ┌─────────────────────────────────────────────────────────────┐
    │  PromptComposer（变量组装器）                                │
    │  - 从 registry 收集所有子系统的 promptVars()                │
    │  - 按 priority merge 成一份 PromptVars                    │
    └─────────────────────────────────────────────────────────────┘
            │
            ▼
    ┌─────────────────────────────────────────────────────────────┐
    │  PromptRenderer（纯函数渲染器）                              │
    │  - 接收 Map<String, String>                                │
    │  - 纯字符串替换，无状态                                      │
    └─────────────────────────────────────────────────────────────┘
            │
            ▼
    ┌─────────────────────────────────────────────────────────────┐
    │  AgentExecutor / ReActLoop（执行引擎，Phase 2 引入）        │
    │  - 通过 ctx.getSubSystem("tool") 获取工具                  │
    │  - 通过 ctx.getSubSystem("hitl") 进行审批检查              │
    │  - 不保存消息、不操作 Memory                                 │
    └─────────────────────────────────────────────────────────────┘

子系统层（均实现 VesselSubSystem）
    ├─ VesselProfile        : 配置画像 + 基础 prompt 变量（priority=0）
    ├─ MemorySubSystem      : ShortMemoryFactory + LongMemoryFactory
    ├─ ToolSubSystem        : @Tool 本地工具 + MCP 客户端
    ├─ SkillSubSystem       : SkillRegistry + Prompt 注入 + read_skill Tool
    ├─ HitlSubSystem        : HitlPolicy + HitlGate + 审批流
    └─ MetricsSubSystem     : StepRecord + TokenUsage + Micrometer（Phase 5）
```

**与 06-04 旧架构的关键差异**：
- 删除 `PromptContextFactory`，配置加载内聚到 `VesselProfile`
- 删除 `AgentExecutionContext`（直接持有 VesselRuntime 导致循环依赖风险），改为 `TaskContext`
- `VesselRuntime` 不再直接持有 `PromptContext`，而是通过 `SubSystemRegistry` 查询 `VesselProfile`
- `PromptComposer` 和 `PromptRenderer` 职责分离

---

## 5. VesselSubSystem SPI（骨架，必须实现）

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

### 5.1 为什么 `configure` 和 `promptVars` 不合并？

| 维度 | `configure(registry)` | `promptVars()` |
|------|----------------------|----------------|
| **调用次数** | 每个 VesselRuntime 实例一次 | 每次任务一次 |
| **核心职责** | 子系统自身初始化 + 获取协作引用 | 返回本次任务的 prompt 变量 |
| **返回值** | void | `PromptVars` |
| **典型工作** | 建立 MCP 连接、加载技能索引 | 注入技能列表、工具描述 |
| **副作用** | 可以（建立连接、缓存配置） | 应无副作用（只读取状态） |

如果合并，子系统需要在每次任务时都重复执行一次性初始化，或者维护复杂的内部状态判断。分离后语义天然清晰。

---

## 6. 辅助类型

### 6.1 MessageThread（替代裸 List<SpiMessage>）

```java
public class MessageThread {
    private final List<SpiMessage> messages = new ArrayList<>();

    public void add(SpiMessage message) { messages.add(message); }
    public List<SpiMessage> snapshot() { return List.copyOf(messages); }
    public boolean isEmpty() { return messages.isEmpty(); }
    public int size() { return messages.size(); }
}
```

### 6.2 StepLog（替代裸 List<StepRecord>）

```java
public class StepLog {
    private final List<StepRecord> steps = new ArrayList<>();

    public void add(StepRecord step) { steps.add(step); }
    public List<StepRecord> snapshot() { return List.copyOf(steps); }
    public int size() { return steps.size(); }
}
```

### 6.3 SubSystemRegistry（不变）

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

## 7. 子系统逐个设计

### 7.0 VesselProfile（内置子系统）

见 **3.1 VesselProfile = Vessel 画像（内置子系统）**。

### 7.1 MemorySubSystem

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

### 7.2 ToolSubSystem

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

**Spring AI Alibaba 通用工具集引入方式**（pom.xml）：

```xml
<!-- Phase 2 引入 -->
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-tool-calling-baidusearch</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-tool-calling-bash</artifactId>
</dependency>
```

**MCP 客户端配置**（application.yml）：

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        stdio:
          connections:
            filesystem:
              command: npx
              args: ["-y", "@modelcontextprotocol/server-filesystem", "/tmp"]
```

### 7.3 HitlSubSystem

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
        List<HitlDecision> decisions = new ArrayList<>();
        List<ApprovalItem> pendingItems = new ArrayList<>();

        for (AssistantMessage.ToolCall tc : toolCalls) {
            ToolCallContext toolCtx = new ToolCallContext(tc.name(), tc.arguments(),
                ctx.getTask().getVesselId(), ctx.getTask().getTaskId(), ctx.getSteps().size() + 1);

            HitlDecision decision = hitlPolicy.decide(toolCtx);
            decisions.add(decision);

            if (decision == HitlDecision.REQUIRE_APPROVAL) {
                pendingItems.add(ApprovalItem.builder()
                    .toolCallId(tc.id())
                    .toolName(tc.name())
                    .argumentsJson(tc.arguments())
                    .displaySummary(tc.name() + ": " + tc.arguments())
                    .build());
            }
        }

        if (!pendingItems.isEmpty()) {
            ApprovalTicket ticket = ApprovalTicket.builder()
                .ticketId(UUID.randomUUID().toString())
                .taskId(ctx.getTask().getTaskId())
                .items(pendingItems)
                .createdAt(Instant.now())
                .build();
            return HitlEvaluation.suspended(ticket, decisions);
        }

        return HitlEvaluation.approved(decisions);
    }

    public ApprovalResolution awaitApproval(ApprovalTicket ticket) {
        return hitlGate.await(ticket);
    }

    public void resolve(String ticketId, ApprovalResolution resolution) {
        hitlGate.resolve(ticketId, resolution);
    }
}
```

**HitlPolicy 默认实现**：

```java
@Component
public class ConfigurableHitlPolicy implements HitlPolicy {

    @Value("${hitl.default-require-approval:false}")
    private boolean defaultRequireApproval;

    private final Set<String> requireApprovalSet = ConcurrentHashMap.newKeySet();
    private final Set<String> skipApprovalSet = ConcurrentHashMap.newKeySet();

    @Override
    public HitlDecision decide(ToolCallContext context) {
        if (skipApprovalSet.contains(context.toolName())) {
            return HitlDecision.APPROVE_AUTO;
        }
        if (requireApprovalSet.contains(context.toolName()) || defaultRequireApproval) {
            return HitlDecision.REQUIRE_APPROVAL;
        }
        return HitlDecision.APPROVE_AUTO;
    }

    public void configure(Set<String> require, Set<String> skip) {
        requireApprovalSet.addAll(require);
        skipApprovalSet.addAll(skip);
    }
}
```

**HitlGate 同步实现（CLI 场景）**：

```java
@Component
@ConditionalOnProperty(name = "meta.claw.channel", havingValue = "cli")
public class CliHitlGate implements HitlGate {

    @Autowired private ApprovalService approvalService;

    @Override
    public ApprovalResolution await(ApprovalTicket ticket) {
        System.out.println("\n🔒 以下工具调用需要审批：");
        ticket.items().forEach(item -> System.out.println("  - " + item.toolName() + ": " + item.argumentsJson()));
        System.out.print("批准全部? (Y/n): ");

        String input = new java.util.Scanner(System.in).nextLine().trim();
        boolean approved = input.isEmpty() || input.equalsIgnoreCase("Y") || input.equalsIgnoreCase("yes");

        Map<String, ApprovalStatus> decisions = new HashMap<>();
        ticket.items().forEach(item -> decisions.put(item.toolCallId(),
            approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED));

        ApprovalResolution resolution = ApprovalResolution.builder()
            .ticketId(ticket.ticketId())
            .decisions(decisions)
            .operator("cli-user")
            .build();

        approvalService.resolve(ticket.ticketId(), resolution);
        return resolution;
    }

    @Override
    public void resolve(String ticketId, ApprovalResolution resolution) {
        approvalService.resolve(ticketId, resolution);
    }
}
```

### 7.4 SkillSubSystem

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

**Skill 文件结构**：

```text
~/.meta-claw/
├── skills/                      # 系统级技能
│   └── travel-planner/
│       └── SKILL.md
└── vessels/{vessel}/
    └── skills/                  # Vessel 私有技能（覆盖系统级）
        └── code-reviewer/
            └── SKILL.md
```

**SKILL.md 格式**：

```markdown
---
name: travel-planner
description: 需要规划旅行路线时使用该技能，提供按天的行程安排和预算建议。
---

# 旅行计划助手
当用户需要规划旅行时，请按以下步骤思考：
1. 询问出发地、目的地、旅行天数和大致预算。
2. 推荐景点和活动，并合理安排到每一天。
3. 估算每日的花费并提供总预算。
```

**read_skill Tool**（供 LLM 按需读取全文）：

```java
@ToolService
public class SkillReadTool {

    @Autowired private SkillSubSystem skillSubSystem;

    @Tool(description = "读取指定技能的完整指令文档。当需要使用某个技能时调用此工具。")
    public String readSkill(@ToolParam(description = "技能名称，如 travel-planner") String skillName) {
        return skillSubSystem.readSkillContent(skillName);
    }
}
```

### 7.5 MetricsSubSystem（预留，Phase 5）

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

## 8. VesselRuntime 重构为编排器

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

    /** 所有子系统，Spring 自动收集（含 VesselProfile） */
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
            // Phase 1：直接调用 LLM（单轮对话）
            // Phase 2+：改为 agentExecutor.execute(ctx, request)
            List<SpiMessage> messages = buildLlmRequest(task, systemPrompt);
            SpiChatRequest request = SpiChatRequest.builder()
                .messages(messages)
                .sessionId(task.getSessionId())
                .build();

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
        // TODO: Phase 3 实现 agentExecutor.resume(ctx, resolution)
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

## 9. PromptComposer 与 PromptRenderer 分离

### 9.1 PromptComposer（组装器）

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

### 9.2 PromptRenderer（纯函数渲染器）

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

## 10. ReActLoop 与 AgentExecutor（远期设计，Phase 2+ 引入）

### 10.1 为什么 Phase 1 不引入？

Phase 1 的目标是让 `./init.sh` 通过、CLI `chat default` 仍能对话。当前系统已支持单轮对话，直接调用 `llmClient.chat(request)` 即可工作。引入 `ReActLoop` 需要 `ToolSubSystem` 先落地，否则循环中没有工具可调用。

### 10.2 Phase 2 的 AgentExecutor 设计

```java
@Component
public class AgentExecutor {

    @Value("${vessel.agent.max-steps:50}")
    private int maxSteps;

    public Reply execute(TaskContext ctx, SpiChatRequest request) {
        // 通过 registry 获取子系统
        ToolSubSystem toolSub = ctx.getSubSystem("tool");
        HitlSubSystem hitlSub = ctx.getSubSystem("hitl");
        LlmClientManager llmClient = ...; // 从 Spring 注入或 ctx 获取

        List<ToolCallback> tools = toolSub != null ? toolSub.getToolCallbacks() : List.of();
        List<SpiMessage> messages = new ArrayList<>(request.getMessages());

        for (int step = 1; step <= maxSteps; step++) {
            SpiChatResponse response = llmClient.chat(
                SpiChatRequest.builder().messages(messages).sessionId(request.getSessionId()).build()
            );

            if (response == null || !hasToolCalls(response)) {
                return new Reply(ReplyType.TEXT, response.content());
            }

            List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(response);

            // HITL 检查
            if (hitlSub != null) {
                HitlEvaluation evaluation = hitlSub.evaluate(toolCalls, ctx);
                if (evaluation.hasSuspensions()) {
                    throw new HitlSuspendedException(evaluation.getTicket());
                }
            }

            // 执行工具
            List<SpiMessage.ToolResult> results = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : toolCalls) {
                ToolCallback callback = findToolCallback(tc.name(), tools);
                String result = callback.call(tc.arguments());
                results.add(new SpiMessage.ToolResult(tc.id(), tc.name(), result));
            }
            messages.add(SpiMessage.toolResults(results));
            ctx.getMessages().add(SpiMessage.toolResults(results));

            ctx.getSteps().add(StepRecord.builder().stepNumber(step).build());
        }

        throw new AgentException("超过最大步数: " + maxSteps);
    }

    public Reply resume(TaskContext ctx, ApprovalResolution resolution) {
        // 从 resolution 恢复挂起的工具调用，继续 ReAct 循环
        // ...
        return null;
    }
}
```

### 10.3 ReActLoop 通过 TaskContext 获取子系统

```java
// ReActLoop 或 AgentExecutor 内部
ToolSubSystem toolSub = ctx.getSubSystem("tool");
HitlSubSystem hitlSub = ctx.getSubSystem("hitl");
SkillSubSystem skillSub = ctx.getSubSystem("skill");
MemorySubSystem memSub = ctx.getSubSystem("memory");
```

**关键约束**：ReActLoop 不保存消息、不操作 Memory。消息保存由 `VesselRuntime` 在调用前后负责，Memory 操作由 `MemorySubSystem` 提供接口。

---

## 11. 实施路线图

### Phase 1：SPI 骨架 + 现有能力迁移（P0）

**目标**：搭好新 `VesselSubSystem` SPI，以 `Task` 为根重塑命名，把现有 Memory 能力包装为子系统，VesselRuntime 升级为编排器。

| 步骤 | 任务 | 文件 |
|------|------|------|
| 1.1 | 新建 `PromptVars` | `meta-claw-core/.../prompt/PromptVars.java` |
| 1.2 | 新建 `VesselProfile`（内置子系统） | `meta-claw-core/.../runtime/VesselProfile.java` |
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
| 1.13 | 删除 `PromptContextFactory` 及相关类 | 全仓清理 |
| 1.14 | 全量编译 + P0 测试 + `./init.sh` | 全仓 |

**验证**：`./init.sh` 通过，CLI `chat default` 仍能对话。

### Phase 2：Tool 子系统 + Spring AI 1.1.7 + ReActLoop（P0）

**目标**：支持 @Tool、MCP、Spring AI Alibaba 通用工具，引入 ReActLoop 实现多轮 tool-call。

| 步骤 | 任务 |
|------|------|
| 2.1 | 新建 `ToolSubSystem`，实现 `promptVars()` 注入工具列表 |
| 2.2 | 改造 `LlmClientManager`：支持单次 `call(messages, tools)` |
| 2.3 | 新建 `AgentExecutor` / `ReActLoop`，通过 `ctx.getSubSystem("tool")` 获取工具 |
| 2.4 | 修改 `VesselRuntime.execute()`：从直接调用 LLM 改为 `agentExecutor.execute(ctx, request)` |
| 2.5 | 引入 `spring-ai-starter-mcp-client`，配置 filesystem MCP Server |
| 2.6 | 引入 Spring AI Alibaba 通用工具集（按需） |
| 2.7 | **验证**：LLM 能调用本地 Tool + MCP Tool |

### Phase 3：HITL 子系统（P0）

**目标**：实现人工审核闭环。

| 步骤 | 任务 |
|------|------|
| 3.1 | 新建 `HitlSubSystem`、`HitlPolicy`、`HitlGate`、`ApprovalService` |
| 3.2 | 实现 `CliHitlGate`（终端阻塞审批） |
| 3.3 | `ReActLoop` 集成 `ctx.getSubSystem("hitl").evaluate()` |
| 3.4 | `VesselRuntime.resume()` 恢复挂起任务 |
| 3.5 | **验证**：配置敏感工具需审批，终端 Y/n 交互后恢复 |

### Phase 4：Skill 子系统（P1）

**目标**：实现渐进式披露的技能体系。

| 步骤 | 任务 |
|------|------|
| 4.1 | 新建 `SkillSubSystem`、`SkillRegistry`、`SkillLoader` |
| 4.2 | 扫描 `~/.meta-claw/skills/` 和 `vessels/<vessel>/skills/` |
| 4.3 | `SkillSubSystem.promptVars()` 注入技能摘要 |
| 4.4 | 新建 `SkillReadTool`（`@Tool`） |
| 4.5 | **验证**：创建 SKILL.md，LLM 按需调用 read_skill |

### Phase 5：Metrics + 流式 + 多 Agent（P2）

**目标**：生产级可观测性和高级能力。

| 步骤 | 任务 |
|------|------|
| 5.1 | 新建 `MetricsSubSystem`，接入 Micrometer |
| 5.2 | 流式 `ReActLoop`（`stream()` 方法） |
| 5.3 | `VesselManager` 自动刷新（WatchService） |
| 5.4 | 多 Agent 协作（`TeamContext`） |

---

## 12. 与 06-04 旧设计的对比

| 维度 | 06-04 旧设计（已废弃） | 本版（v2） | 修正理由 |
|------|----------------------|-----------|----------|
| 执行上下文 | `AgentExecutionContext`（直接持有 VesselRuntime） | **`TaskContext`**（持有 VesselTask + registry） | 消除循环依赖，命名与入参 `VesselTask` 对应 |
| Prompt 变量 | 无明确概念，`contribute(PromptContext.Builder)` 命令式注入 | **`PromptVars`** 返回式，支持 `merge()` | 职责清晰，无副作用，纯函数式 |
| 子系统贡献 | `contribute(PromptContext.Builder)` | **`promptVars()` 返回 PromptVars** | 子系统只贡献变量，不操作 Prompt 文本 |
| 运行时状态 | `PromptContext`（由 PromptContextFactory 创建） | **`VesselProfile`**（内置子系统，priority=0） | 本质是配置画像，不是 Prompt 专属；走统一注册机制 |
| SPI 生命周期 | `initialize(VesselRuntime)` | **`configure(SubSystemRegistry)`** | 避免传递整个 Runtime，只传 registry |
| 生命周期钩子 | `onExecutionStart/End` | **`onTaskStart/End`** | 与 `Task` 命名层次对齐 |
| 渲染器入参 | `PromptContext` | **`Map<String, String>`** | 渲染器纯函数，只依赖字符串变量 |
| Prompt 组装 | VesselRuntime 直接 `buildPromptContext()` | **`PromptComposer` + `PromptRenderer` 分离** | Composer 组装变量，Renderer 纯渲染 |
| 消息列表 | `List<SpiMessage>` | **`MessageThread`** | 封装 + 不可变快照 |
| 步骤记录 | `List<StepRecord>` | **`StepLog`** | 封装 + 不可变快照 |
| ChatMessageManager | 强调"唯一消息管家" | **VesselRuntime 直接操作 MemorySubSystem** | 06-06 已验证可工作，简化骨架；未来如需统一消息管理可引入 |
| AgentExecutor | Phase 1 即引入 | **Phase 1 直接调用 LLM，Phase 2 引入 AgentExecutor** | 先让单轮对话可工作，再引入多轮 ReAct |

---

## 13. 自检清单

- [x] `configure` 与 `promptVars` 的分离理由已文档化（调用次数、职责、返回值）
- [x] `TaskContext` 与 `VesselTask` 的关系已明确（参数 vs 执行环境）
- [x] `PromptVars` 的语义已明确（prompt 模板变量集合，可 merge）
- [x] `VesselProfile` 替代 `PromptContext` 的理由已说明（本质是配置画像，同时作为内置子系统注册到 registry）
- [x] 所有 Phase 均有具体文件和验证步骤
- [x] 无 "TBD"/"TODO" 模糊描述（TODO 仅用于标注跨 Phase 的待实现点）
- [x] 与 06-06 设计文档完全一致（SPI 签名、类名、方法名、调用顺序）
- [x] 已删除 `PromptContextFactory`、`AgentExecutionContext`、`initialize(VesselRuntime)`、`contribute(PromptContext.Builder)` 等旧概念

---

*文档版本：v2.0（基于 06-06 修正版）*
*替代文档：*
- *`2026-06-04-meta-claw-agent-platform-longterm-design.md`*
