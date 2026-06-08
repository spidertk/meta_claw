# Meta-Claw Agent 平台长远设计：Spring AI 1.1.7 + SubSystem SPI + HITL + Skills

> 日期：2026-06-04
> 原则：**不做简化，面向长远。子系统 SPI 是骨架，Spring AI 1.1.7 是引擎，HITL 和 Skills 是能力。**

---

## 1. 设计原则（约束条件）

1. **VesselSubSystem SPI 必须存在**：所有能力（Memory、Tool、Skill、HITL、Metrics、Knowledge、Cron）必须作为子系统接入，不得直接硬编码在 VesselRuntime 中。
2. **Spring AI 1.1.7 原生能力最大化复用**：@Tool、MCP、ToolCallback、ChatClient Advisor 等框架能力优先复用，不重复造轮子。
3. **消息生命周期统一管理**：`ChatMessageManager` 是唯一消息管家，ShortMemoryAdvisor 委托它，各子系统通过它操作 Memory。
4. **渐进式实现**：先搭 SPI 骨架，再填子系统血肉。每阶段结束必须能跑通 `./init.sh`。

---

## 2. 总体架构：子系统编排模式

```text
Channel (CLI / Gateway / Weixin)
    ↓ EventBus
AgentLoop
    ├── VesselManager（路由、获取 Runtime）
    └── VesselRuntime.chat() / .execute()
            │
            ▼
    ┌─────────────────────────────────────────────────────────────┐
    │  VesselRuntime（子系统编排器，不是执行器）                    │
    │  - 持有 List<VesselSubSystem>，按 priority 排序             │
    │  - 创建 AgentExecutionContext                               │
    │  - 生命周期：initialize() → onExecutionStart() → delegate   │
    │            → onExecutionEnd() → finalize()                  │
    └─────────────────────────────────────────────────────────────┘
            │
            ▼
    ┌─────────────────────────────────────────────────────────────┐
    │  AgentExecutor（执行编排器）                                 │
    │  - 接收 AgentExecutionContext                               │
    │  - 调用 ChatMessageManager 保存 user 消息                   │
    │  - 加载历史、构建 Prompt                                    │
    │  - 调用 ReActLoop                                           │
    │  - 保存 assistant 消息                                      │
    └─────────────────────────────────────────────────────────────┘
            │
            ▼
    ┌─────────────────────────────────────────────────────────────┐
    │  ReActLoop（纯执行引擎）                                     │
    │  - 通过 ctx.getRuntime().getLlmClient() 调用 LLM            │
    │  - 解析 tool_calls，通过 ctx.getToolSubSystem() 获取工具     │
    │  - HITL 检查：通过 ctx.getHitlSubSystem().evaluate()         │
    │  - 不保存消息、不操作 Memory                                 │
    └─────────────────────────────────────────────────────────────┘

子系统层（均实现 VesselSubSystem）
    ├─ MemorySubSystem      : ShortMemoryFactory + LongMemoryFactory 接入
    ├─ ToolSubSystem        : @Tool 本地工具 + MCP 客户端 + Spring AI Alibaba 通用工具集
    ├─ SkillSubSystem       : SkillRegistry + Prompt 注入 + read_skill Tool
    ├─ HitlSubSystem        : HitlPolicy + HitlGate + 审批流
    └─ MetricsSubSystem     : StepRecord + TokenUsage + Micrometer（Phase 5）
```

---

## 3. VesselSubSystem SPI（骨架，必须实现）

```java
package meta.claw.core.runtime.subsystem;

import meta.claw.core.agent.AgentExecutionContext;
import meta.claw.core.prompt.PromptContext;
import meta.claw.core.runtime.VesselRuntime;

/**
 * Vessel 子系统 SPI。
 * 所有能力（Memory, Tool, Skill, HITL, Metrics, Knowledge, Cron）必须实现此接口，
 * 由 VesselRuntime 统一编排生命周期和对话上下文注入。
 */
public interface VesselSubSystem {

    /** 子系统唯一标识，如 "memory", "tool", "skill", "hitl" */
    String name();

    /** 初始化：VesselRuntime 创建时调用 */
    void initialize(VesselRuntime runtime);

    /** 每次对话前调用，向 PromptContext 注入内容 */
    void contribute(PromptContext.Builder contextBuilder);

    /** 每次对话开始时调用 */
    default void onExecutionStart(AgentExecutionContext ctx) {}

    /** 每次对话结束时调用 */
    default void onExecutionEnd(AgentExecutionContext ctx) {}

    /** 子系统优先级，数值越小越早执行 contribute */
    default int priority() { return 100; }
}
```

### 3.1 VesselRuntime 作为子系统编排器

```java
package meta.claw.core.runtime;

@Slf4j
@Component
@Scope("prototype")
public class VesselRuntime implements InitializingBean {

    @Autowired private PromptContextFactory promptContextFactory;
    @Autowired private PromptRenderer promptRenderer;
    @Autowired private ChatMessageManager chatMessageManager;
    @Autowired private LlmClientManager llmClientManager;
    @Autowired private AgentExecutor agentExecutor;

    /** 所有子系统，Spring 自动收集 */
    @Autowired private List<VesselSubSystem> subSystems;

    private PromptContext promptContext;
    private final String vesselId;

    public VesselRuntime(String vesselId) {
        this.vesselId = vesselId;
    }

    @Override
    public void afterPropertiesSet() {
        this.promptContext = promptContextFactory.create(vesselId);
        // 初始化所有子系统
        subSystems.stream()
            .sorted(Comparator.comparingInt(VesselSubSystem::priority))
            .forEach(sub -> sub.initialize(this));
    }

    public PromptContext getPromptContext() {
        return promptContext;
    }

    public VesselConfig getConfig() {
        return promptContext.getBundle().getRuntimeVesselConfig();
    }

    public ChatMessageManager getChatMessageManager() {
        return chatMessageManager;
    }

    public LlmClientManager getLlmClient() {
        return llmClientManager;
    }

    public List<VesselSubSystem> getSubSystems() {
        return Collections.unmodifiableList(subSystems);
    }

    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T getSubSystem(String name) {
        return (T) subSystems.stream()
            .filter(s -> s.name().equals(name))
            .findFirst()
            .orElse(null);
    }

    public VesselTask newTask(String sessionId, String userMessage) {
        return VesselTask.builder()
            .taskId(UUID.randomUUID().toString())
            .vesselId(this.vesselId)
            .sessionId(sessionId)
            .userMessage(userMessage)
            .createdAt(Instant.now())
            .build();
    }

    /**
     * 构建带所有子系统贡献的 PromptContext
     */
    public PromptContext buildPromptContext() {
        PromptContext.Builder builder = promptContext.toBuilder();
        subSystems.stream()
            .sorted(Comparator.comparingInt(VesselSubSystem::priority))
            .forEach(sub -> sub.contribute(builder));
        return builder.build();
    }

    public Reply chat(String sessionId, String userMessage) {
        return execute(newTask(sessionId, userMessage));
    }

    public Reply execute(VesselTask task) {
        AgentExecutionContext ctx = new AgentExecutionContext(task, this);

        // 子系统生命周期钩子：onExecutionStart
        subSystems.forEach(sub -> sub.onExecutionStart(ctx));

        try {
            Reply reply = agentExecutor.execute(ctx);
            return reply;
        } finally {
            // 子系统生命周期钩子：onExecutionEnd
            subSystems.forEach(sub -> sub.onExecutionEnd(ctx));
        }
    }

    public Reply resume(VesselTask task, ApprovalResolution resolution) {
        AgentExecutionContext ctx = new AgentExecutionContext(task, this);
        return agentExecutor.resume(ctx, resolution);
    }
}
```

---

## 4. 子系统逐个设计

### 4.1 MemorySubSystem（已有能力包装为子系统）

```java
package meta.claw.core.runtime.subsystem;

@Component
public class MemorySubSystem implements VesselSubSystem {

    @Autowired private ShortMemoryFactory shortMemoryFactory;
    @Autowired private LongMemoryFactory longMemoryFactory;

    private VesselRuntime runtime;

    @Override
    public String name() { return "memory"; }

    @Override
    public void initialize(VesselRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void contribute(PromptContext.Builder builder) {
        // Memory 子系统目前不向 Prompt 注入内容
        // 未来可注入会话摘要、长期偏好等
    }

    public ShortMemory getShortMemory() {
        return shortMemoryFactory.get(runtime.getPromptContext().getBundle()
            .getMemoryConfig().getShortTermStore());
    }

    public LongMemory getLongMemory() {
        return longMemoryFactory.get(runtime.getPromptContext().getBundle()
            .getMemoryConfig().getLongTermStore());
    }
}
```

### 4.2 ToolSubSystem（整合 Spring AI 1.1.7 通用工具集）

**职责**：
- 收集本地 `@Tool` Bean（通过 Spring AI `ToolCallbacks.from()`）
- 连接 MCP 客户端（`spring-ai-starter-mcp-client`）
- 引入 Spring AI Alibaba 通用工具集（baidusearch、bash、python 等）
- 向 ReActLoop 提供统一的 `List<ToolCallback>`

```java
package meta.claw.core.runtime.subsystem;

@Component
public class ToolSubSystem implements VesselSubSystem {

    @Autowired private ToolRegistry toolRegistry;  // 现有 @ToolService Bean 收集器
    @Autowired(required = false) private List<ToolCallbackProvider> mcpToolProviders;

    @Override
    public String name() { return "tool"; }

    @Override
    public void initialize(VesselRuntime runtime) { }

    @Override
    public void contribute(PromptContext.Builder builder) {
        // 工具信息注入 Prompt（可选，让 LLM 知道有哪些工具可用）
    }

    /**
     * 聚合所有工具：本地 @Tool + MCP + Alibaba 通用工具
     */
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

        // 3. Spring AI Alibaba 通用工具集（通过 starter 自动注入为 Spring Bean）
        // 这些工具已实现 ToolCallback 接口，会被 Spring 自动收集到 ApplicationContext
        // 如果有特定命名空间，可按需过滤

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
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-starter-tool-calling-docloader</artifactId>
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

### 4.3 HitlSubSystem（人工审核）

参考 Spring AI Alibaba 的 `HumanInTheLoopHook` 设计。

```java
package meta.claw.core.runtime.subsystem;

@Component
public class HitlSubSystem implements VesselSubSystem {

    @Autowired private HitlPolicy hitlPolicy;
    @Autowired private HitlGate hitlGate;

    @Override
    public String name() { return "hitl"; }

    @Override
    public void initialize(VesselRuntime runtime) { }

    @Override
    public void contribute(PromptContext.Builder builder) {
        // 可在 System Prompt 中注入 HITL 规则说明
    }

    /**
     * 评估一组 tool calls 是否需要审批。
     * 由 ReActLoop 调用。
     */
    public HitlEvaluation evaluate(List<AssistantMessage.ToolCall> toolCalls, AgentExecutionContext ctx) {
        List<HitlDecision> decisions = new ArrayList<>();
        List<ApprovalItem> pendingItems = new ArrayList<>();

        for (AssistantMessage.ToolCall tc : toolCalls) {
            ToolCallContext toolCtx = new ToolCallContext(tc.name(), tc.arguments(),
                ctx.getVesselId(), ctx.getTask().getTaskId(), ctx.getTask().getSteps().size() + 1);

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

### 4.4 SkillSubSystem（渐进式披露）

参考 Spring AI Alibaba 的 `SkillRegistry` + `SkillsAgentHook`。

```java
package meta.claw.core.runtime.subsystem;

@Component
public class SkillSubSystem implements VesselSubSystem {

    @Autowired private SkillRegistry skillRegistry;

    @Override
    public String name() { return "skill"; }

    @Override
    public void initialize(VesselRuntime runtime) {
        skillRegistry.load(runtime.getConfig().getIdentity().getId());
    }

    @Override
    public void contribute(PromptContext.Builder builder) {
        // 向 System Prompt 注入技能摘要（名称 + 描述），不注入全文
        List<SkillInfo> infos = skillRegistry.getAvailableSkills().stream()
            .map(s -> SkillInfo.builder().name(s.name()).description(s.description()).build())
            .toList();
        builder.skills(infos);
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

### 4.5 MetricsSubSystem（预留，Phase 5）

```java
@Component
public class MetricsSubSystem implements VesselSubSystem {

    @Autowired private MeterRegistry meterRegistry;

    @Override
    public String name() { return "metrics"; }

    @Override
    public void initialize(VesselRuntime runtime) { }

    @Override
    public void onExecutionEnd(AgentExecutionContext ctx) {
        VesselTask task = ctx.getTask();
        meterRegistry.counter("agent.task.completed", "vessel", task.getVesselId()).increment();
        meterRegistry.counter("agent.steps", "vessel", task.getVesselId()).increment(task.getSteps().size());
    }
}
```

---

## 5. AgentExecutionContext 增强（携带子系统引用）

```java
package meta.claw.core.agent;

@Getter
public class AgentExecutionContext {
    private final VesselTask task;
    private final VesselRuntime runtime;
    private final PromptContext promptContext;
    private final ChatMessageManager chatMessageManager;
    private final MemoryConfig memoryConfig;
    private final List<Message> messages;

    public AgentExecutionContext(VesselTask task, VesselRuntime runtime) {
        this.task = task;
        this.runtime = runtime;
        this.promptContext = runtime.buildPromptContext();  // 包含所有子系统贡献
        this.chatMessageManager = runtime.getChatMessageManager();
        this.memoryConfig = promptContext.getBundle().getMemoryConfig();
        this.messages = new ArrayList<>();
    }

    // 便捷方法：通过 VesselRuntime 获取子系统
    public ToolSubSystem getToolSubSystem() {
        return runtime.getSubSystem("tool");
    }

    public HitlSubSystem getHitlSubSystem() {
        return runtime.getSubSystem("hitl");
    }

    public SkillSubSystem getSkillSubSystem() {
        return runtime.getSubSystem("skill");
    }

    public MemorySubSystem getMemorySubSystem() {
        return runtime.getSubSystem("memory");
    }

    // ... 其他便捷方法
}
```

---

## 6. ReActLoop（通过 Context 获取子系统）

```java
@Component
public class ReActLoop {

    @Value("${vessel.agent.max-steps:50}")
    private int maxSteps;

    public SpiChatResponse run(AgentExecutionContext ctx) {
        VesselTask task = ctx.getTask();
        VesselConfig config = ctx.getPromptContext().getBundle().getRuntimeVesselConfig();
        LlmClientManager llmClient = ctx.getRuntime().getLlmClient();

        // 通过 ToolSubSystem 获取工具
        List<ToolCallback> tools = ctx.getToolSubSystem().getToolCallbacks();
        List<Message> messages = ctx.getMessagesSnapshot();

        for (int step = 1; step <= maxSteps; step++) {
            ChatResponse response = llmClient.call(messages, tools, config);
            Generation gen = response.getResult();

            if (gen == null || !hasToolCalls(gen)) {
                return extractResponse(response);
            }

            List<AssistantMessage.ToolCall> toolCalls = gen.getOutput().getToolCalls();

            // 通过 HitlSubSystem 评估审批
            HitlEvaluation evaluation = ctx.getHitlSubSystem().evaluate(toolCalls, ctx);
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

            task.appendStep(StepRecord.builder().stepNumber(step).build());
        }

        throw new AgentException("超过最大步数: " + maxSteps);
    }
}
```

---

## 7. 实施路线图（面向长远，分阶段）

### Phase 1：子系统骨架 + 现有能力迁移（P0）

**目标**：搭好 `VesselSubSystem` SPI，把现有 Memory、Prompt 能力包装为子系统，VesselRuntime 升级为编排器。

1. 新建 `VesselSubSystem` 接口
2. 新建 `MemorySubSystem`，将现有 `ShortMemoryFactory` / `LongMemoryFactory` 接入
3. 新建 `ChatMessageManager`，接管所有消息保存/加载
4. 改造 `VesselRuntime`：移除直接注入的 Factory，改为注入 `List<VesselSubSystem>`，提供 `getSubSystem(name)`
5. 改造 `ShortMemoryAdvisor`：委托 `ChatMessageManager`
6. 改造 `AgentExecutor` + `ReActLoop`：接收 `AgentExecutionContext`
7. **验证**：`./init.sh` 通过，CLI `chat default` 仍能对话

### Phase 2：Tool 子系统 + Spring AI 1.1.7 通用工具集（P0）

**目标**：支持 @Tool、MCP、Spring AI Alibaba 通用工具。

1. 新建 `ToolSubSystem`
2. 改造 `LlmClientManager`：移除 `ToolCallAdvisor`，新增单次 `call()` 方法
3. `ReActLoop` 通过 `ctx.getToolSubSystem().getToolCallbacks()` 获取工具
4. 引入 `spring-ai-starter-mcp-client`，配置 filesystem MCP Server
5. 引入 `spring-ai-alibaba-starter-tool-calling-baidusearch` 等通用工具（按需）
6. **验证**：LLM 能调用 CalculatorTool + MCP filesystem 工具

### Phase 3：HITL 子系统（P0）

**目标**：实现人工审核闭环。

1. 新建 `HitlSubSystem`、`HitlPolicy`、`HitlGate`、`ApprovalService`
2. 实现 `CliHitlGate`（终端阻塞审批）
3. `ReActLoop` 集成 `ctx.getHitlSubSystem().evaluate()`
4. `VesselRuntime.resume()` 恢复挂起任务
5. **验证**：配置敏感工具需审批，终端 Y/n 交互后恢复

### Phase 4：Skill 子系统（P1）

**目标**：实现渐进式披露的技能体系。

1. 新建 `SkillSubSystem`、`SkillRegistry`、`SkillLoader`
2. 扫描 `~/.meta-claw/skills/` 和 `vessels/<vessel>/skills/`
3. `SkillSubSystem.contribute()` 向 Prompt 注入技能摘要
4. 新建 `SkillReadTool`（@Tool）
5. **验证**：创建 travel-planner SKILL.md，LLM 按需调用 read_skill

### Phase 5：Metrics + 流式 + 多 Agent（P2）

**目标**：生产级可观测性和高级能力。

1. 新建 `MetricsSubSystem`，接入 Micrometer
2. 流式 ReActLoop（`stream()` 方法）
3. `VesselManager` 自动刷新（WatchService）
4. 多 Agent 协作（`TeamContext`）

---

## 8. 与原始设计文档的对齐声明

| 原始设计（2026-05-22） | 本方案 | 对齐状态 |
|------------------------|--------|----------|
| `VesselSubSystem` SPI | ✅ 完整保留，所有子系统必须实现 | 对齐 |
| `VesselRuntime` 子系统编排器 | ✅ 注入 `List<VesselSubSystem>`，按 priority 排序 | 对齐 |
| `VesselExecutionContext` | ✅ 拆分为 `VesselTask` + `AgentExecutionContext` | 增强 |
| `VesselToolLoop` / ReAct | ✅ `ReActLoop` 职责一致 | 对齐 |
| `ToolExecutor` 统一反射 | ✅ 复用 Spring AI `ToolCallback` | 对齐 |
| `VesselExecutionMetrics` | ✅ `MetricsSubSystem` Phase 5 落地 | 对齐 |
| Memory 子系统 | ✅ `MemorySubSystem` + `ChatMessageManager` | 增强 |
| Knowledge 子系统 | ⏳ Phase 4/5 引入 | 预留 |
| Skill 子系统 | ✅ `SkillSubSystem` Phase 4 落地 | 对齐 |
| HITL 审批流 | ✅ `HitlSubSystem` Phase 3 落地 | 对齐 |

---

*文档版本：v1.0（长远设计版）*
*自检结论：与原始设计文档总路径一致，子系统 SPI 为骨架，Spring AI 1.1.7 为引擎，边界清晰。*
