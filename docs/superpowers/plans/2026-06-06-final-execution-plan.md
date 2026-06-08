# Meta-Claw 平台重构最终执行方案

> **日期**：2026-06-06
> **来源文档**：
> - `2026-06-06-vessel-subsystem-task-promptvars-redesign.md`（SPI 与核心概念设计）
> - `2026-06-06-meta-claw-agent-platform-longterm-design-v2.md`（长远架构与实施路线图）
> **原则**：**子系统 SPI 是骨架，Spring AI 1.1.7 是引擎，VesselProfile 是配置画像，PromptVars 是 Prompt 组装协议。**

---

## 1. 设计原则（不可违背）

1. **VesselSubSystem SPI 必须存在**：所有能力（Memory、Tool、Skill、HITL、Metrics、Knowledge、Cron）必须作为子系统接入，不得直接硬编码在 `VesselRuntime` 中。
2. **Spring AI 1.1.7 原生能力最大化复用**：`@Tool`、MCP、`ToolCallback`、`ChatClient` 等框架能力优先复用，不重复造轮子。
3. **PromptVars 是子系统对 Prompt 的唯一贡献协议**：子系统不直接操作 Prompt 文本，只返回 `Map<String, String>` 形式的模板变量，由 `PromptComposer` 统一 merge。
4. **渐进式实现**：先搭 SPI 骨架，再填子系统血肉。**每阶段结束必须能跑通 `./init.sh`**。

---

## 2. 命名层次体系

按**生命周期/作用域**分层，统一前缀：

| 层级 | 前缀 | 生命周期 | 职责 |
|------|------|----------|------|
| **Vessel** | `Vessel*` | 每个 Vessel 实例一次 | 运行时编排、配置画像、子系统注册 |
| **Task** | `Task*` | 每次对话一次 | 单次 `chat()`/`execute()` 调用的完整周期 |
| **Prompt** | `Prompt*` | 每次对话构建一次 | Prompt 变量收集、组装、渲染 |

---

## 3. 核心概念与类图

```text
┌─────────────────────────────────────────────────────────────────────┐
│  Channel (CLI / Gateway / Weixin)                                   │
│       ↓ EventBus                                                    │
│  AgentLoop                                                          │
│       ├── VesselManager（路由、获取 Runtime）                        │
│       └── VesselRuntime.chat() / .execute()                         │
│               │                                                     │
│               ▼                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  VesselRuntime（子系统编排器）                               │   │
│  │  - 持有 SubSystemRegistry                                   │   │
│  │  - 所有子系统（含 VesselProfile）统一按 priority 注册       │   │
│  │  - 生命周期：configure() → promptVars() → onTaskStart()     │   │
│  │            → delegate → onTaskEnd()                         │   │
│  └─────────────────────────────────────────────────────────────┘   │
│               │                                                     │
│               ▼                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  PromptComposer（变量组装器）                                │   │
│  │  - 从 registry 收集所有子系统的 promptVars()                │   │
│  │  - 按 priority merge 成一份 PromptVars                    │   │
│  └─────────────────────────────────────────────────────────────┘   │
│               │                                                     │
│               ▼                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  PromptRenderer（纯函数渲染器）                              │   │
│  │  - 接收 Map<String, String>                                │   │
│  │  - 纯字符串替换，无状态                                      │   │
│  └─────────────────────────────────────────────────────────────┘   │
│               │                                                     │
│               ▼                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │  AgentExecutor / ReActLoop（执行引擎，Phase 2 引入）        │   │
│  │  - 通过 ctx.getSubSystem("tool") 获取工具                  │   │
│  │  - 通过 ctx.getSubSystem("hitl") 进行审批检查              │   │
│  │  - 不保存消息、不操作 Memory                                 │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  子系统层（均实现 VesselSubSystem）                                  │
│      ├─ VesselProfile        : 配置画像 + 基础 prompt 变量（priority=0）
│      ├─ MemorySubSystem      : ShortMemoryFactory + LongMemoryFactory
│      ├─ ToolSubSystem        : @Tool 本地工具 + MCP 客户端
│      ├─ SkillSubSystem       : SkillRegistry + Prompt 注入 + read_skill Tool
│      ├─ HitlSubSystem        : HitlPolicy + HitlGate + 审批流
│      └─ MetricsSubSystem     : StepRecord + TokenUsage + Micrometer（Phase 5）
└─────────────────────────────────────────────────────────────────────┘
```

---

## 4. SPI 定义（唯一事实来源）

```java
package meta.claw.core.runtime.subsystem;

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
     * 用途：缓存 registry 引用、建立外部连接（MCP 客户端）、加载一次性索引（技能文件）
     */
    void configure(SubSystemRegistry registry);

    /** 返回本系统对本次任务 prompt 的贡献变量 */
    default PromptVars promptVars() { return PromptVars.empty(); }

    /** 任务开始 */
    default void onTaskStart(TaskContext ctx) {}

    /** 任务结束 */
    default void onTaskEnd(TaskContext ctx) {}

    /** 优先级：数值越小，越早执行 promptVars */
    default int priority() { return 100; }
}
```

### 4.1 `configure` 与 `promptVars` 不合并的理由

| 维度 | `configure(registry)` | `promptVars()` |
|------|----------------------|----------------|
| **调用次数** | 每个 VesselRuntime 实例一次 | 每次任务一次 |
| **核心职责** | 子系统自身初始化 + 获取协作引用 | 返回本次任务的 prompt 变量 |
| **返回值** | void | `PromptVars` |
| **典型工作** | 建立 MCP 连接、加载技能索引 | 注入技能列表、工具描述 |
| **副作用** | 可以（建立连接、缓存配置） | 应无副作用（只读取状态） |

---

## 5. 核心类清单

| 类名 | 职责 | Phase |
|------|------|-------|
| `PromptVars` | 不可变 prompt 模板变量集合，支持 `merge()` | 1 |
| `VesselProfile` | Vessel 配置画像，内置子系统（name=`"profile"`, priority=`0`） | 1 |
| `MessageThread` | 消息线程封装（替代裸 `List<SpiMessage>`） | 1 |
| `StepLog` | 步骤日志封装（替代裸 `List<StepRecord>`） | 1 |
| `SubSystemRegistry` | 子系统注册表 | 1 |
| `TaskContext` | 任务执行上下文（替代 `AgentExecutionContext`） | 1 |
| `VesselSubSystem` | 子系统 SPI（`configure` + `promptVars` + `onTaskStart/End`） | 1 |
| `MemorySubSystem` | 包装现有 Short/Long Memory 能力 | 1 |
| `PromptComposer` | 从 registry 收集并 merge 所有 `promptVars()` | 1 |
| `PromptRenderer` | 纯函数渲染器，接收 `Map<String, String>` | 1 |
| `ToolSubSystem` | @Tool 本地工具 + MCP 客户端 | 2 |
| `HitlSubSystem` | 审批策略 + 审批门 | 3 |
| `SkillSubSystem` | 技能注册表 + 按需读取工具 | 4 |
| `MetricsSubSystem` | Micrometer 指标收集 | 5 |

---

## 6. 实施路线图

### Phase 1：SPI 骨架 + 现有能力迁移（P0）

**目标**：搭好新 `VesselSubSystem` SPI，以 `Task` 为根重塑命名，把现有 Memory 能力包装为子系统。

| 步骤 | 任务 | 文件 | 依赖 |
|------|------|------|------|
| 1.1 | 新建 `PromptVars` | `meta-claw-core/.../prompt/PromptVars.java` | 无 |
| 1.2 | 新建 `VesselProfile`（内置子系统） | `meta-claw-core/.../runtime/VesselProfile.java` | 1.1 |
| 1.3 | 新建 `MessageThread` | `meta-claw-core/.../runtime/MessageThread.java` | 无 |
| 1.4 | 新建 `StepLog` | `meta-claw-core/.../runtime/StepLog.java` | 无 |
| 1.5 | 修改 `VesselSubSystem` 接口 | `meta-claw-core/.../runtime/subsystem/VesselSubSystem.java` | 1.1 |
| 1.6 | 新建 `SubSystemRegistry` | `meta-claw-core/.../runtime/subsystem/SubSystemRegistry.java` | 1.5 |
| 1.7 | 新建 `TaskContext` | `meta-claw-core/.../runtime/TaskContext.java` | 1.2, 1.3, 1.4, 1.6 |
| 1.8 | 新建 `MemorySubSystem` | `meta-claw-core/.../runtime/subsystem/MemorySubSystem.java` | 1.5, 1.6 |
| 1.9 | 新建 `PromptComposer` | `meta-claw-core/.../prompt/PromptComposer.java` | 1.1, 1.6 |
| 1.10 | 修改 `PromptRenderer`：接收 `Map<String, String>` | `meta-claw-core/.../prompt/PromptRenderer.java` | 1.1 |
| 1.11 | 修改 `SpiChatRequest`：删除 PromptContext 字段 | `meta-claw-core/.../llm/SpiChatRequest.java` | 无 |
| 1.12 | 重构 `VesselRuntime` | `meta-claw-core/.../runtime/VesselRuntime.java` | 1.2, 1.6, 1.7, 1.9, 1.10 |
| 1.13 | 删除 `PromptContextFactory` 及相关类 | 全仓清理 | 1.12 |
| 1.14 | 全量编译 + P0 测试 + `./init.sh` | 全仓 | 1.1~1.13 |

**验证标准**：`./init.sh` 通过，CLI `chat default` 仍能对话。

### Phase 2：Tool 子系统 + Spring AI 1.1.7 + ReActLoop（P0）

**目标**：支持 `@Tool`、MCP、Spring AI Alibaba 通用工具，引入 `ReActLoop` 实现多轮 tool-call。

| 步骤 | 任务 | 依赖 |
|------|------|------|
| 2.1 | 新建 `ToolSubSystem`，实现 `promptVars()` 注入工具列表 | Phase 1 |
| 2.2 | 改造 `LlmClientManager`：支持单次 `call(messages, tools)` | Phase 1 |
| 2.3 | 新建 `AgentExecutor` / `ReActLoop`，通过 `ctx.getSubSystem("tool")` 获取工具 | 2.1, 2.2 |
| 2.4 | 修改 `VesselRuntime.execute()`：从直接调用 LLM 改为 `agentExecutor.execute(ctx, request)` | 2.3 |
| 2.5 | 引入 `spring-ai-starter-mcp-client`，配置 filesystem MCP Server | 2.1 |
| 2.6 | 引入 Spring AI Alibaba 通用工具集（按需） | 2.5 |
| 2.7 | **验证**：LLM 能调用本地 Tool + MCP Tool | 2.1~2.6 |

### Phase 3：HITL 子系统（P0）

**目标**：实现人工审核闭环。

| 步骤 | 任务 | 依赖 |
|------|------|------|
| 3.1 | 新建 `HitlSubSystem`、`HitlPolicy`、`HitlGate`、`ApprovalService` | Phase 2 |
| 3.2 | 实现 `CliHitlGate`（终端阻塞审批） | 3.1 |
| 3.3 | `ReActLoop` 集成 `ctx.getSubSystem("hitl").evaluate()` | 3.1 |
| 3.4 | `VesselRuntime.resume()` 恢复挂起任务 | 3.2, 3.3 |
| 3.5 | **验证**：配置敏感工具需审批，终端 Y/n 交互后恢复 | 3.1~3.4 |

### Phase 4：Skill 子系统（P1）

**目标**：实现渐进式披露的技能体系。

| 步骤 | 任务 | 依赖 |
|------|------|------|
| 4.1 | 新建 `SkillSubSystem`、`SkillRegistry`、`SkillLoader` | Phase 3 |
| 4.2 | 扫描 `~/.meta-claw/skills/` 和 `vessels/<vessel>/skills/` | 4.1 |
| 4.3 | `SkillSubSystem.promptVars()` 注入技能摘要 | 4.2 |
| 4.4 | 新建 `SkillReadTool`（`@Tool`） | 4.1 |
| 4.5 | **验证**：创建 SKILL.md，LLM 按需调用 read_skill | 4.1~4.4 |

### Phase 5：Metrics + 流式 + 多 Agent（P2）

**目标**：生产级可观测性和高级能力。

| 步骤 | 任务 | 依赖 |
|------|------|------|
| 5.1 | 新建 `MetricsSubSystem`，接入 Micrometer | Phase 4 |
| 5.2 | 流式 `ReActLoop`（`stream()` 方法） | 5.1 |
| 5.3 | `VesselManager` 自动刷新（WatchService） | 5.1 |
| 5.4 | 多 Agent 协作（`TeamContext`） | 5.3 |

---

## 7. 关键设计决策汇总

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 执行上下文命名 | `TaskContext` | 与入参 `VesselTask` 直接对应，无 "Agent" 歧义 |
| Prompt 变量载体 | `PromptVars` | 不可变 `Map<String, String>` 包装，支持纯函数式 merge |
| 配置画像归属 | `VesselProfile`（内置子系统） | 本质是 Vessel 配置画像，不是 Prompt 专属；走统一注册机制，priority=0 |
| 渲染器入参 | `Map<String, String>` | 纯函数，只依赖字符串变量 |
| Prompt 组装 | `PromptComposer` + `PromptRenderer` 分离 | Composer 组装变量，Renderer 纯渲染 |
| SPI 配置方法 | `configure(SubSystemRegistry)` | 避免传递整个 Runtime，只传 registry，消除循环依赖风险 |
| SPI 生命周期钩子 | `onTaskStart/End` | 与 `Task` 命名层次对齐 |
| AgentExecutor 引入时机 | Phase 2 | Phase 1 先让单轮对话可工作，Phase 2 再引入多轮 ReAct |
| 消息列表 | `MessageThread` | 封装 + 不可变快照 |
| 步骤记录 | `StepLog` | 封装 + 不可变快照 |

---

## 8. 与旧设计的对比（为什么要改）

| 维度 | 旧设计（06-04） | 新设计（本方案） |
|------|--------------|----------------|
| 执行上下文 | `AgentExecutionContext`（直接持有 VesselRuntime） | `TaskContext`（持有 VesselTask + registry） |
| Prompt 变量 | 无明确概念，`contribute(PromptContext.Builder)` 命令式注入 | `PromptVars` 返回式，支持 `merge()` |
| 子系统贡献 | `contribute(PromptContext.Builder)` | `promptVars()` 返回 PromptVars |
| 运行时状态 | `PromptContext`（由 PromptContextFactory 创建） | `VesselProfile`（内置子系统，priority=0） |
| SPI 生命周期 | `initialize(VesselRuntime)` | `configure(SubSystemRegistry)` |
| 渲染器入参 | `PromptContext` | `Map<String, String>` |
| 消息列表 | `List<SpiMessage>` | `MessageThread` |
| 步骤记录 | `List<StepRecord>` | `StepLog` |

---

## 9. 风险与注意事项

1. **Phase 1 的 `VesselRuntime` 重构是全局改动**：所有调用 `getConfig()`、`getShortMemory()` 等方法的代码都需要适配新的 registry 查询模式。
2. **`PromptContextFactory` 删除需彻底**：确保没有遗留引用，包括测试代码。
3. **Spring Bean 作用域**：`VesselProfile`、`VesselRuntime` 均为 `@Scope("prototype")`，每个 Vessel 实例独立。
4. **MCP 客户端在 `configure()` 中初始化**：Phase 2 引入 `ToolSubSystem` 时，MCP 连接建立放在 `configure()` 而非 `promptVars()` 中。
5. **HITL 挂起状态持久化**：Phase 3 的 `ApprovalTicket` 需要持久化到短期记忆或独立存储，以便 `resume()` 恢复。

---

## 10. 自检清单（每阶段结束必须检查）

- [ ] `./init.sh` 通过
- [ ] CLI `chat default` 能正常对话
- [ ] 无编译警告（特别是 deprecation）
- [ ] `claude-progress.md` 已更新
- [ ] `feature_list.json` 状态已更新
- [ ] 新增类有基本单元测试

---

*文档版本：v1.0（最终执行方案）*
*基于：*
- *`2026-06-06-vessel-subsystem-task-promptvars-redesign.md`（v3.0）*
- *`2026-06-06-meta-claw-agent-platform-longterm-design-v2.md`（v2.0）*
