# Meta-Claw 与 Spring AI Alibaba 双引擎 Agent 执行抽象设计

> 日期：2026-06-15  
> 范围：`meta-claw-core` 运行时层、`meta-claw-tool` 工具生态  
> 目标：将 Agent 执行引擎抽象为可插拔 SPI，底层统一使用 Spring AI，上层可切换“自研 VesselSubSystem + ReAct 循环”或“Spring AI Alibaba ReactAgent/Graph”。

---

## 1. 执行摘要

**一句话概括：meta-claw 已经建成以 `VesselSubSystem` SPI 为核心的单 Agent 运行时，Spring AI Alibaba 则提供成熟的 Graph 状态机与多 Agent 编排能力；通过 `AgentEngine` 接口把两者封装为可切换实现，可以在不破坏现有投资的前提下，渐进引入 Alibaba 的 Graph/HITL/多 Agent 能力。**

### 1.1 实现进度总览

| 阶段 | 状态 | 关键交付物 | 验证方式 |
|------|------|-----------|---------|
| **Phase 0** | ✅ 已完成 | `spring-ai-alibaba-agent-framework` / `spring-ai-alibaba-graph-core` 依赖；`AlibabaEngineSmokeTest` | `./init.sh` 编译 + 测试通过 |
| **Phase 1** | ✅ 已完成 | `AgentEngine` SPI、`AgentEngineFactory`、`NativeAgentEngine`；`VesselRuntime` 工厂路由；`VesselConfig.agentEngine` / `AlibabaAgentConfig` | `./init.sh` 全量 P0 测试通过 |
| **Phase 2** | ✅ 已完成 | `SpiMessageConverter`、`ReactAgentFactory`、`SpringAiAlibabaAgentEngine`（同步 call）；Vessel 模板补充 engine 示例 | `./init.sh` 全量 P0 测试通过 |
| **Phase 3** | ✅ 已完成 | 流式 `executeStream`、`MetaClawAgentMetricsHook`、`MetaClawModelMetricsHook` | `./init.sh` 全量 P0 测试通过 |
| **Phase 4** | ✅ 已完成 | `MetaClawHitlHook`、Alibaba 引擎 HITL 恢复 | `./init.sh` 全量 P0 测试通过 |
| **Phase 5** | 🔄 进行中 | 多 Agent 编排（Sequential / Parallel / Routing / Supervisor）；Step 7 配置模型已完成，Step 8 SAA 接入待实施 | `./init.sh` 全量 P0 测试通过 |
| **Phase 6** | ⬜ 未开始 | `VesselCheckpointSaver` 持久化 SAA thread 状态 | 待实施 |
| **可选深化** | ⬜ 未开始 | `ExecutableTool` SPI + 工具执行层隔离 | 待评估 |

> 最新进度维护：本表随代码实现同步更新。最近一次更新 2026-06-16，Phase 0+1+2+3+4 已完成并通过 `./init.sh`；Phase 5 Step 7（多 Agent 配置模型）已完成，Step 8（SAA 多 Agent 调用接入）待实施。

当前仓库已完成：
- Spring Boot 3.5.15 + Spring AI 1.1.8 + Spring AI Alibaba 1.1.2.3 的混合基线升级；
- 自研 `VesselSubSystem` SPI（Profile/Memory/Tool/HITL/Skill/Metrics）+ `AgentExecutor` / `StreamingAgentExecutor` ReAct 循环；
- `ToolSubSystem` 统一收敛本地 `@Tool`、Spring AI `@Tool`、MCP 工具为 `List<ToolCallback>`；
- HITL 审批与恢复、Skill 按需加载、Metrics 观测。

下一步的核心问题是：**执行引擎应该继续自研，还是引入 Spring AI Alibaba 的 ReactAgent/Graph？** 本方案给出的答案是：**不做二选一，而是通过接口抽象让两者并存、可配置切换。**

### 1.2 资深用户视角：当前实现不足点

> 本节从“实际使用 Alibaba 引擎”的资深用户角度，列出截至 2026-06-16 仍然明显影响可用性或可维护性的实现缺口。这些问题不影响 `./init.sh` 通过，但会决定生产环境中 `agent_engine: alibaba` 是否真正可用。

| 不足点 | 当前影响 | 建议后续动作 |
|--------|---------|-------------|
| **多 Agent 编排“半悬空”** | Phase 5 Step 7 已完成配置模型（`agents` / `flow`），但 Step 8 未实施。用户可以在 YAML 里声明多个子 Agent 和编排模式，可切换 `agent_engine: alibaba` 后这些配置不会被实际执行，仍是单 ReactAgent 路径。 | 实现 `SaaMultiAgentFactory`，在 `SpringAiAlibabaAgentEngine` 中按 `flow.mode` 分发到 `SequentialAgent` / `ParallelAgent` / `LlmRoutingAgent`。 |
| **Alibaba 引擎缺少真实 LLM 端到端验证** | 当前 P0 测试以 Mockito 模拟 `ReactAgent` 为主；`AlibabaEngineSmokeTest` 只验证能构造 ReactAgent 并调用一次无工具对话。真实模型返回的 `reasoningContent`、`toolCalls`、`usage` 在 `AssistantMessage.metadata` 中的格式尚未被真实网络调用验证。 | 在受控环境（配置有效 API Key）下跑通一次真实的 tool-call 对话与流式对话，固化预期行为。 |
| **HITL 流式路径未完整覆盖** | Phase 4 的 HITL 中断/恢复基于 `ReactAgent.call()` 与 `AFTER_MODEL` Hook。`executeStream()` 触发 HITL 时，Flux 能否正确中断、`SpiStreamingCallback.onHitlSuspend` 能否收集到完整 ticket、恢复后能否继续流式输出，目前没有明确测试。 | 补充 `SpringAiAlibabaAgentEngine` 流式 HITL 的单元/集成测试，并在 CLI 真实交互中验证。 |
| **Checkpoint 持久化未开始** | Phase 6 的 `VesselCheckpointSaver` 尚未实施。Alibaba 引擎的 HITL 中断状态仍依赖内存中的 `TaskContext` 和 `ApprovalTicket`；进程重启后 ticket 与图中间状态丢失。 | 调研 `BaseCheckpointSaver` 接口，实现以 `taskId` + `threadId` 为 key 的文件持久化，并在 `ReactAgentFactory` 中配置。 |
| **工具执行层未完全解耦** | 可选深化任务未开始。`AgentExecutor` / `StreamingAgentExecutor` 仍直接 `import org.springframework.ai.tool.ToolCallback`，未来若要接入非 Spring AI 协议的工具（HTTP 函数、Python 远端、A2A）会牵连执行层。 | 定义 `ExecutableTool` SPI，创建 `SpringAiToolCallbackAdapter`，逐步把 `ToolSubSystem.getToolCallbacks()` 改为 `getExecutableTools()`。 |
| **ReactAgentFactory 缺少实例缓存** | 为避免 Hook 中的 `TaskContext` 串用，当前按请求构建 ReactAgent。高频对话或高并发场景下会重复编译 SAA Graph，带来可观测的构建开销。 | 引入 Vessel 级缓存，但确保 Hook 在每次调用时拿到当前 `TaskContext`（例如通过 ThreadLocal 或请求级 wrapper）。 |
| **Metrics Hook 的 token usage 依赖 SAA 内部 metadata** | `MetaClawModelMetricsHook` 从 `AssistantMessage.getMetadata()` 中读取 `usage`。SAA 是否总是填充、键名是否稳定，尚未经真实 LLM 验证；SAA 版本升级后可能 silently 失效。 | 真实调用后确认 metadata 结构；必要时增加 `ModelInterceptor` 或从 `ChatResponse` 显式取 usage。 |
| **SAA 版本依赖风险持续存在** | SAA 1.1.2.3 官方编译依赖 Spring AI 1.1.2，仓库使用 1.1.8。当前编译和单测通过，但真实运行时仍可能遇到二进制不兼容（如 `NoSuchMethodError`、`NoClassDefFoundError`）。 | 在 `./init.sh` 外增加真实 LLM 调用验证；关注 SAA 发布说明，必要时升级或降级 Spring AI 版本。 |
| **编排配置类型安全不足** | `AgentFlowConfig.mode` 当前以 `String` 存储，运行时通过 `getModeEnum()` 转换。用户拼写错误（如 `sequental`）要到执行阶段才能发现，而不是配置加载阶段。 | SnakeYAML 支持大小写不敏感枚举反序列化后，将 `mode` 改为 `AgentFlowMode` 枚举字段。 |
| **Prompt 协议差异未完全弥合** | SAA 的 `systemPrompt` / `instruction` 与 meta-claw 的 `PromptComposer` 多子系统变量协议尚未对齐。当前把完整 messages 传入 ReactAgent，但未把 `PromptVars` 动态注入 SAA instruction，可能损失部分子系统能力。 | 在 `ReactAgentFactory` 中探索把 `PromptComposer` 输出映射为 SAA `systemPrompt` / `instruction`，保持两种引擎的 prompt 一致性。 |
| **CLI/Gateway 层 engine 切换验收不足** | 当前验证集中在 `meta-claw-core`。CLI 和 Gateway 是否能在真实运行时正确切换 engine、流式输出与 HITL 在 UI 层表现如何，还没有端到端验收。 | 在 CLI 真实启动路径中验证 `agent_engine: alibaba` 的聊天、流式、HITL 三条路径。 |

---

## 2. 当前状态：meta-claw + Spring AI Alibaba 混合架构

### 2.1 已落地能力

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CLI / Gateway                                   │
│                         (ChatCommand / AgentLoop)                            │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ chat(sessionId, message)
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         VesselManager                                        │
│   扫描 vessels/ 目录 → 加载 VesselConfig → 创建 VesselRuntime(prototype)      │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ getRuntime(vesselId)
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         VesselRuntime                                        │
│  核心编排器：SubSystemRegistry 统一调度 Profile/Memory/Tool/Hitl/Skill/Metrics │
│  执行入口：chat() / execute() / chatStream() / resume()                      │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │
           ┌────────────────────┼────────────────────┐
           ▼                    ▼                    ▼
┌──────────────────┐  ┌─────────────────────┐  ┌─────────────────────────┐
│  AgentExecutor    │  │ StreamingAgentExecutor│  │      LlmClientManager    │
│  同步 ReAct 循环  │  │  流式 ReAct 循环      │  │  Spring AI ChatClient    │
│                  │  │                      │  │  适配层                  │
└──────────────────┘  └─────────────────────┘  └─────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         Spring AI ChatClient / ChatModel                    │
│         ToolCallback / ToolCallAdvisor / ShortMemoryAdvisor                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 与 Spring AI Alibaba 的关系

| 层级 | meta-claw 自研 | Spring AI Alibaba | 当前关系 |
|------|----------------|-------------------|---------|
| 配置与 Vessel 定义 | `VesselProfile` / `VesselMeta` / `RuntimeConfigResolver` | - | 自研 |
| 子系统 SPI | `VesselSubSystem` + `SubSystemRegistry` | `Agent` / `StateGraph` / `CompiledGraph` | 自研 |
| Prompt 组装 | `PromptComposer` + `PromptRenderer` | `systemPrompt` / `instruction` / Agent Builder | 自研 |
| Agent 执行循环 | `AgentExecutor` / `StreamingAgentExecutor` | `ReactAgent` / `AgentLlmNode` / `AgentToolNode` | 自研 |
| 工具抽象 | `ToolSubSystem` 收敛为 `List<ToolCallback>` | 复用 Spring AI `ToolCallback` | 混合（自研收敛，底层 Spring AI） |
| LLM 调用 | `LlmClientManager` → Spring AI `ChatClient` | `ChatModel` / `ChatClient` | 底层统一 Spring AI |
| 记忆 | `ShortMemory` / `LongMemory` + Factory | `BaseCheckpointSaver` / `MemorySaver` | 自研 |
| HITL | `HitlSubSystem` + `ApprovalTicket` | `HumanInTheLoopHook` + checkpoint | 自研 |
| Metrics | `MetricsRecorder` (Micrometer) | Micrometer / ARMS / Langfuse | 自研 |
| 多 Agent 编排 | 未实现 | `SequentialAgent` / `ParallelAgent` / `LlmRoutingAgent` / `Supervisor` | 缺失 |

**结论**：meta-claw 在“Vessel 定义、子系统 SPI、Prompt 工程、记忆、HITL、Metrics”上已有较完整的自研体系；Spring AI Alibaba 在“Graph 状态机、多 Agent 编排、上下文工程”上更成熟。两者都以 Spring AI 作为 LLM/工具的底层协议，具备统一抽象的基础。

---

## 3. 两套 Agent 执行框架对比

### 3.1 meta-claw 自研模型：子系统编排 + 手动 ReAct

**优势**：
1. **与 Vessel 概念深度融合**：每个 Vessel 是一组子系统的组合，`PromptComposer` 让所有子系统通过 `PromptVars` 协议动态贡献 prompt 变量，适合“数字员工”场景。
2. **HITL 与审批流高度可控**：`HitlSubSystem` 支持按工具名黑白名单，`ApprovalTicket` / `ApprovalResolution` / `HitlGate` 完全由自己定义，同步/流式两条路径都已实现。
3. **记忆后端可插拔**：`ShortMemoryFactory` / `LongMemoryFactory` 通过 Spring `Map<String, Store>` 自动收集实现，新增后端只需声明 `@Component`。
4. **代码可控、调试直观**：`AgentExecutor.reactLoop()` 是手写循环，每一步的 MessageThread、StepLog、ToolCall 都在 `TaskContext` 中可见，便于单测和日志追踪。
5. **已有大量测试与 CLI 验证**：`./init.sh` 已覆盖 P0 测试集，切换风险低。

**不足**：
1. **多 Agent 编排缺失**：当前只有单 Vessel 执行，没有 Sequential / Parallel / Routing / Supervisor 等模式。
2. **状态机中断/恢复能力弱**：HITL 通过异常 + `resume()` 实现，中间状态靠 `TaskContext` 内存维护，进程重启后 ticket 丢失。
3. **上下文工程扩展点不足**：没有像 SAA 那样的 `AgentHook` / `ModelHook` / `ToolInterceptor` 标准扩展协议。
4. **PromptRenderer 占位符硬编码**：新增子系统变量需要同步改渲染器，扩展性受限。
5. **工具抽象未完全隔离**：`AgentExecutor` 直接依赖 `org.springframework.ai.tool.ToolCallback`，未来若要换工具协议会牵连执行层。

### 3.2 Spring AI Alibaba 模型：Graph 状态机 + ReactAgent

**优势**：
1. **Graph 编排能力成熟**：`StateGraph` + `CompiledGraph` 支持条件边、并行分支、子图嵌套、聚合策略，天然适合多 Agent 工作流。
2. **ReAct 开箱即用**：`ReactAgent.builder()` 一行配置即可获得循环、工具调用、流式、记忆 checkpoint。
3. **丰富的预置工具生态**：40+ tool-calling starters（搜索、地图、IM、数据库等），可直接复用。
4. **上下文工程扩展点多**：`AgentHook` / `ModelHook` / `ToolInterceptor` / `ModelInterceptor` / `StreamingModelInterceptor` 覆盖 Agent/模型/工具/流式全链路。
5. **HITL 与 checkpoint 原生支持**：`HumanInTheLoopHook` + `BaseCheckpointSaver`（Memory/Redis/JDBC）实现状态机中断/恢复。
6. **多 Agent 模式完整**：Sequential / Parallel / Routing / Supervisor / Agent as Tool / A2A 等模式已内置。
7. **与 Spring 生态深度集成**：starter 自动配置、Bean 注入，接入成本低。

**不足**：
1. **版本匹配敏感**：SAA 1.1.2.3 官方编译依赖 Spring AI 1.1.2，meta-claw 使用 1.1.8，跨版本覆盖需持续验证二进制兼容性。
2. **迭代快、API 变化多**：1.1.x 中部分方法已废弃（如 `chatClient(ChatClient)`），文档与社区资料以中文为主。
3. **Prompt 组装不如 meta-claw 灵活**：SAA 的 Prompt 主要是 Agent 级 `systemPrompt` / `instruction`，缺少由多个子系统动态贡献变量的协议。
4. **状态机调试门槛高**：`OverAllState` / `KeyStrategy` / `NodeOutput` 对使用者不够直观。
5. **HITL 与自定义审批流耦合**：若要把 meta-claw 的 `HitlSubSystem` 策略接入，需要写一个 `ModelHook` 桥接。
6. **DashScope/百炼生态绑定较深**：虽然底层是 Spring AI，但部分高级功能（如模型服务市场）与阿里云生态强相关。

### 3.3 对比矩阵

| 维度 | meta-claw 自研 | Spring AI Alibaba | 说明 |
|------|----------------|-------------------|------|
| 执行入口 | `VesselRuntime.chat()/execute()/chatStream()` | `ReactAgent.call()/invoke()/stream()` | meta-claw 以 Vessel 为入口，SAA 以 Agent 为入口 |
| 编排核心 | `SubSystemRegistry` + `VesselSubSystem` SPI | `StateGraph` + `CompiledGraph` | 前者是子系统组合，后者是状态图 |
| 执行循环 | 手动 `reactLoop()` | `AgentLlmNode` → 条件边 → `AgentToolNode` | 都基于 ReAct |
| 状态载体 | `TaskContext` | `OverAllState` | SAA 状态机更完整 |
| 生命周期扩展 | `configure()` / `promptVars()` / `onTaskStart/End` | `AgentHook` / `ModelHook` / `ToolInterceptor` | SAA 扩展点更丰富 |
| Prompt 组装 | `PromptComposer` 多子系统变量合并 | Agent Builder 注入 systemPrompt | meta-claw 更灵活 |
| 工具来源 | `ToolSubSystem` 收敛本地 + MCP | `.tools()` / `.methodTools()` / tool starters | 两者都基于 Spring AI `ToolCallback` |
| HITL | 异常驱动 + `resume()` | `HumanInTheLoopHook` + checkpoint | meta-claw 审批流更可控，SAA 恢复能力更强 |
| 记忆 | `MemorySubSystem` + 可插拔 Store | `BaseCheckpointSaver` | meta-claw 长期记忆更独立 |
| 多 Agent | ❌ 未实现 | ✅ 内置多种模式 | SAA 明显领先 |
| 流式 | `StreamingAgentExecutor` + callback | `Flux<NodeOutput>` / `Flux<Message>` | 都已支持 |
| 可观测性 | `MetricsRecorder` 手工记录 | Micrometer / ARMS / Langfuse | SAA 生态更丰富 |
| 版本稳定性 | 自研，可控 | 快速迭代，版本敏感 | meta-claw 风险低，SAA 能力新 |

---

## 4. 建议方案：AgentEngine SPI + 双实现

### 4.1 核心设计思想

1. **不废弃现有投资**：`VesselSubSystem` SPI、`PromptComposer`、`MemorySubSystem`、`HitlSubSystem`、`MetricsSubSystem` 继续保留并演进。
2. **把 Agent 执行引擎抽出来**：`VesselRuntime` 不再直接依赖 `AgentExecutor` / `StreamingAgentExecutor`，而是依赖 `AgentEngine` 接口。
3. **双实现可切换**：
   - `NativeAgentEngine`：复用现有 `AgentExecutor` / `StreamingAgentExecutor`；
   - `SpringAiAlibabaAgentEngine`：基于 `ReactAgent` / `CompiledGraph`。
4. **底层统一 Spring AI**：两种实现都复用 `LlmClientProviderManager`、`ToolSubSystem.getToolCallbacks()`、`SpiMessage` 抽象。
5. **渐进迁移**：先让 Alibaba 引擎跑通同步单步对话，再逐步接入流式、HITL、多 Agent。

### 4.2 价值

| 价值点 | 说明 |
|--------|------|
| 保护现有投资 | CLI、P0 测试、HITL、Skill、Metrics 不需要推倒重来 |
| 能力可扩展 | 未来需要多 Agent 编排时，可直接切换到 SAA 实现 |
| 风险可控 | 通过配置切换，灰度验证，出问题时回滚到 native |
| 生态复用 | 40+ SAA tool starters、Graph 可视化、DashScope 深度能力可渐进接入 |
| 统一底层 | LLM、工具、记忆都继续走 Spring AI，不引入两套协议 |

---

## 5. 技术实现细节

### 5.1 AgentEngine SPI

```java
package meta.claw.core.runtime.engine;

import meta.claw.core.message.Reply;
import meta.claw.core.message.SpiChatRequest;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import meta.claw.core.runtime.stream.SpiStreamingCallback;

/**
 * Agent 执行引擎 SPI。
 *
 * <p>实现类负责把 {@link SpiChatRequest} 转换为一次 Agent 任务执行，
 * 并返回最终 {@link Reply}。同步、流式、HITL 恢复三种入口必须同时提供。</p>
 *
 * <p>该接口刻意保持最小化：只接收 TaskContext 和 SpiChatRequest，
 * 不暴露任何 Spring AI 或 SAA 专有类型，保证上层 VesselRuntime 与引擎实现解耦。</p>
 */
public interface AgentEngine {

    /** 同步执行一次对话任务。 */
    Reply execute(TaskContext ctx, SpiChatRequest request);

    /** 流式执行一次对话任务。 */
    void executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback);

    /** 从 HITL 挂起状态恢复并继续执行。 */
    Reply resume(TaskContext ctx, SpiChatRequest request,
                 ApprovalTicket ticket, ApprovalResolution resolution);

    /** 引擎名称，用于配置选择，如 {@code native} 或 {@code alibaba}。 */
    String name();
}
```

### 5.2 AgentEngineFactory

```java
package meta.claw.core.runtime.engine;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 根据 Vessel 配置选择 AgentEngine 实现。
 *
 * <p>所有 {@link AgentEngine} 实现由 Spring 自动收集，按 {@code name()} 注册。
 * 默认引擎为 {@code native}，可通过 {@code vessel.agent.engine} 切换。</p>
 */
@Component
public class AgentEngineFactory {

    private final Map<String, AgentEngine> engines = new ConcurrentHashMap<>();

    @Autowired
    public void setEngines(List<AgentEngine> engineList) {
        engines.clear();
        for (AgentEngine engine : engineList) {
            AgentEngine previous = engines.put(engine.name(), engine);
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate AgentEngine name: " + engine.name());
            }
        }
    }

    public AgentEngine getEngine(String name) {
        AgentEngine engine = engines.get(name);
        if (engine == null) {
            throw new IllegalArgumentException(
                "No AgentEngine for name: " + name + ". Available: " + engines.keySet());
        }
        return engine;
    }

    public AgentEngine getDefaultEngine() {
        return getEngine("native");
    }
}
```

### 5.3 VesselRuntime 改造点

当前 `VesselRuntime` 直接注入 `AgentExecutor` / `StreamingAgentExecutor`：

```java
// 改造前
@Autowired private AgentExecutor agentExecutor;
@Autowired private StreamingAgentExecutor streamingAgentExecutor;
```

改造后：

```java
// 改造后
@Autowired private AgentEngineFactory agentEngineFactory;

private AgentEngine currentEngine() {
    String engineName = getProfile().getBundle()
        .getRuntimeVesselConfig()
        .getAgentEngine(); // 默认 native
    return agentEngineFactory.getEngine(engineName);
}

public Reply execute(VesselTask task) {
    TaskContext ctx = buildTaskContext(task);
    SpiChatRequest request = buildLlmRequest(ctx);
    try {
        return currentEngine().execute(ctx, request);
    } finally {
        onTaskEnd(ctx);
    }
}

public void chatStream(String sessionId, String message, SpiStreamingCallback callback) {
    TaskContext ctx = buildTaskContext(...);
    SpiChatRequest request = buildLlmRequest(ctx);
    try {
        currentEngine().executeStream(ctx, request, callback);
    } finally {
        onTaskEnd(ctx);
    }
}

public Reply resume(VesselTask task, ApprovalTicket ticket, ApprovalResolution resolution) {
    TaskContext ctx = buildTaskContext(task);
    SpiChatRequest request = buildLlmRequest(ctx);
    try {
        return currentEngine().resume(ctx, request, ticket, resolution);
    } finally {
        onTaskEnd(ctx);
    }
}
```

> **注意**：按项目规范，业务类使用 `@Autowired` 字段注入或 setter 注入，不定义显式构造方法。

### 5.4 NativeAgentEngine 实现

```java
package meta.claw.core.runtime.engine;

import meta.claw.core.message.Reply;
import meta.claw.core.message.SpiChatRequest;
import meta.claw.core.runtime.AgentExecutor;
import meta.claw.core.runtime.StreamingAgentExecutor;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import meta.claw.core.runtime.stream.SpiStreamingCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 复用现有 {@link AgentExecutor} / {@link StreamingAgentExecutor} 的本地引擎。
 */
@Component
public class NativeAgentEngine implements AgentEngine {

    @Autowired
    private AgentExecutor agentExecutor;
    @Autowired
    private StreamingAgentExecutor streamingAgentExecutor;

    @Override
    public Reply execute(TaskContext ctx, SpiChatRequest request) {
        return agentExecutor.execute(ctx, request);
    }

    @Override
    public void executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback) {
        streamingAgentExecutor.execute(ctx, request, callback);
    }

    @Override
    public Reply resume(TaskContext ctx, SpiChatRequest request,
                        ApprovalTicket ticket, ApprovalResolution resolution) {
        return agentExecutor.resume(ctx, request, ticket, resolution);
    }

    @Override
    public String name() {
        return "native";
    }
}
```

### 5.5 SpringAiAlibabaAgentEngine 实现

```java
package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import meta.claw.core.message.Reply;
import meta.claw.core.message.SpiChatRequest;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import meta.claw.core.runtime.stream.SpiStreamingCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring AI Alibaba {@link ReactAgent} 的执行引擎实现。
 *
 * <p>Phase 1 先实现同步 call；Phase 2 接入 streamMessages；
 * Phase 3 接入 HITL Hook；Phase 4 接入多 Agent 编排。</p>
 */
@Component
public class SpringAiAlibabaAgentEngine implements AgentEngine {

    @Autowired
    private ReactAgentFactory reactAgentFactory;

    @Override
    public Reply execute(TaskContext ctx, SpiChatRequest request) {
        ReactAgent agent = reactAgentFactory.get(ctx, request);
        // 注意：这里把 meta-claw 的历史消息 + 当前用户消息一起传入
        List<Message> messages = SpiMessageConverter.toSpringMessages(request.getMessages());
        AssistantMessage result = agent.call(messages);
        return new Reply(ReplyType.TEXT, result.getText());
    }

    @Override
    public void executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback) {
        ReactAgent agent = reactAgentFactory.get(ctx, request);
        List<Message> messages = SpiMessageConverter.toSpringMessages(request.getMessages());

        // Phase 2：接入 SAA streamMessages，并透传到 SpiStreamingCallback
        // 当前可先抛 UnsupportedOperationException 或 fallback 到同步执行
        throw new UnsupportedOperationException(
            "SpringAiAlibabaAgentEngine streaming is planned for Phase 2");
    }

    @Override
    public Reply resume(TaskContext ctx, SpiChatRequest request,
                        ApprovalTicket ticket, ApprovalResolution resolution) {
        // Phase 3：把 ApprovalResolution 中已批准/拒绝的 tool 结果重新注入 messages，再 call
        ReactAgent agent = reactAgentFactory.get(ctx, request);
        List<Message> messages = SpiMessageConverter.toSpringMessages(request.getMessages());
        AssistantMessage result = agent.call(messages);
        return new Reply(ReplyType.TEXT, result.getText());
    }

    @Override
    public String name() {
        return "alibaba";
    }
}
```

### 5.6 ReactAgentFactory

```java
package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.llm.LlmClientProviderManager;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.subsystem.ToolSubSystem;
import meta.claw.core.vessel.VesselProfile;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 根据 {@link VesselProfile} 和运行时上下文构造 {@link ReactAgent} 实例。
 *
 * <p>每个 Vessel 可缓存一个 ReactAgent 实例；如果工具/HITL/模型配置变化，
 * 可通过 {@link #invalidate(String)} 重新构建。</p>
 */
@Component
public class ReactAgentFactory {

    @Autowired
    private LlmClientProviderManager llmClientProviderManager;

    private final Map<String, ReactAgent> cache = new ConcurrentHashMap<>();

    public ReactAgent get(TaskContext ctx, SpiChatRequest request) {
        VesselProfile profile = ctx.getProfile();
        String vesselId = profile.getVesselId();

        return cache.computeIfAbsent(vesselId, id -> build(ctx, request));
    }

    private ReactAgent build(TaskContext ctx, SpiChatRequest request) {
        VesselProfile profile = ctx.getProfile();
        ProviderConfig providerConfig = profile.getBundle().getProviderConfig();
        ChatModel chatModel = llmClientProviderManager.createChatModel(providerConfig);

        ToolSubSystem toolSubSystem = ctx.getSubSystem("tool");
        List<Object> tools = toolSubSystem.getToolCallbacks().stream()
            .map(tc -> (Object) tc)
            .collect(Collectors.toList());

        return ReactAgent.builder()
            .name(profile.getBundle().getVesselName())
            .description(profile.getBundle().getVesselDescription())
            .model(chatModel)
            .systemPrompt(extractSystemPrompt(request))
            .tools(tools.toArray(new Object[0]))
            // Phase 3 接入 .hooks(new MetaClawHitlHook(ctx), new MetaClawMetricsHook(ctx))
            // Phase 4 接入 .compileConfig(...) 与多 Agent 配置
            .build();
    }

    private String extractSystemPrompt(SpiChatRequest request) {
        return request.getMessages().stream()
            .filter(m -> "system".equals(m.getRole()))
            .findFirst()
            .map(SpiMessage::getContent)
            .orElse("");
    }

    public void invalidate(String vesselId) {
        cache.remove(vesselId);
    }
}
```

> **说明**：`LlmClientProviderManager` 需要新增 `createChatModel(ProviderConfig)` 方法。如果当前 provider 实现只暴露 `ChatClient`，可从 `ChatClient` 的实现中解包 `ChatModel`，或重构 provider 直接返回 `ChatModel`。

### 5.7 SpiMessageConverter（关键转换层）

```java
package meta.claw.core.runtime.engine;

import meta.claw.core.message.SpiMessage;
import meta.claw.core.message.SpiToolCall;
import org.springframework.ai.chat.messages.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 在 meta-claw {@link SpiMessage} 与 Spring AI {@link Message} 之间做双向转换。
 *
 * <p>特别注意 tool 消息：必须生成带正确 {@code toolCallId} 的 {@link ToolResponseMessage}，
 * 否则 SAA 的条件边无法把 tool 结果与 assistant 的 tool_calls 对应起来。</p>
 */
public final class SpiMessageConverter {

    private SpiMessageConverter() {}

    public static List<Message> toSpringMessages(List<SpiMessage> messages) {
        List<Message> result = new ArrayList<>(messages.size());
        for (SpiMessage m : messages) {
            result.add(toSpringMessage(m));
        }
        return result;
    }

    public static Message toSpringMessage(SpiMessage m) {
        return switch (m.getRole()) {
            case SYSTEM -> new SystemMessage(m.getContent());
            case USER -> new UserMessage(m.getContent());
            case ASSISTANT -> new AssistantMessage(
                m.getContent(),
                Map.of(),
                toSpringToolCalls(m.getToolCalls())
            );
            case TOOL -> {
                // meta-claw 的 tool content 是 JSON，内部包含 toolCallId / toolName / result
                ToolResult tr = parseToolResultJson(m.getContent());
                yield new ToolResponseMessage(
                    List.of(new ToolResponseMessage.ToolResponse(
                        tr.toolCallId(), tr.toolName(), tr.result())),
                    Map.of()
                );
            }
        };
    }

    private static List<AssistantMessage.ToolCall> toSpringToolCalls(List<SpiToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) return List.of();
        return toolCalls.stream()
            .map(tc -> new AssistantMessage.ToolCall(
                tc.getId(),
                tc.getType(),
                tc.getName(),
                tc.getArgumentsJson()))
            .collect(Collectors.toList());
    }

    private record ToolResult(String toolCallId, String toolName, String result) {}

    private static ToolResult parseToolResultJson(String json) {
        // 使用 ObjectMapper 解析 meta-claw tool result JSON
        // 字段约定：toolCallId, toolName, result
        ...
    }
}
```

> **注意**：当前 `LlmClientManager.toSpringMessage()` 对 tool 消息写死 `id/name="tool"`，在接入 SAA 前必须修复为真实 `toolCallId`。

### 5.8 HITL 桥接：MetaClawHitlHook（Phase 3）

```java
package meta.claw.core.runtime.engine.alibabahook;

import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.model.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.hook.model.ModelResponse;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.*;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;

/**
 * 将 meta-claw {@link HitlSubSystem} 接入 SAA AFTER_MODEL Hook。
 *
 * <p>当模型返回包含 tool_calls 且命中审批策略时，通过抛异常中断图执行；
 * 外部收集 {@link ApprovalResolution} 后，把结果重新注入 messages 再调用。</p>
 */
public class MetaClawHitlHook implements ModelHook {

    private final TaskContext ctx;

    public MetaClawHitlHook(TaskContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public HookPosition getPosition() {
        return HookPosition.AFTER_MODEL;
    }

    @Override
    public ModelResponse handle(ModelRequest request, ModelResponse response, Object... args) {
        List<AssistantMessage.ToolCall> toolCalls = extractToolCalls(response);
        if (toolCalls == null || toolCalls.isEmpty()) {
            return response;
        }

        List<SpiToolCall> spiToolCalls = toolCalls.stream()
            .map(tc -> SpiToolCall.builder()
                .id(tc.id())
                .type(tc.type())
                .name(tc.name())
                .argumentsJson(tc.arguments())
                .build())
            .collect(Collectors.toList());

        HitlSubSystem hitlSubSystem = ctx.getSubSystem("hitl");
        HitlEvaluation eval = hitlSubSystem.evaluate(spiToolCalls, ctx);

        if (eval.hasSuspensions()) {
            // SAA 当前未暴露标准“挂起”异常，可用自定义 RuntimeException 中断图执行
            throw new HitlSuspendedException(eval.getTicket());
        }

        return response;
    }

    private List<AssistantMessage.ToolCall> extractToolCalls(ModelResponse response) {
        // 从 response 中提取 AssistantMessage.ToolCall
        ...
    }
}
```

### 5.9 Metrics 桥接：MetaClawMetricsHook（Phase 3）

```java
package meta.claw.core.runtime.engine.alibabahook;

import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 在 SAA Agent 执行前后记录任务级指标。
 */
public class MetaClawMetricsHook implements AgentHook {

    private final TaskContext ctx;

    @Autowired
    private MetricsRecorder metricsRecorder;

    public MetaClawMetricsHook(TaskContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public HookPosition getPosition() {
        return HookPosition.BEFORE_AGENT;
    }

    @Override
    public void handle(Object... args) {
        ctx.markTaskStart();
    }

    // 还需注册 AFTER_AGENT Hook，记录 agent.task.completed / agent.steps / agent.task.duration
}
```

---

## 6. 配置模型扩展

### 6.1 VesselMeta 新增字段

```java
public class VesselMeta {
    // 已有字段...

    /**
     * Agent 执行引擎类型：native（默认）或 alibaba。
     */
    private String agentEngine = "native";

    /**
     * Alibaba 引擎专属配置。
     */
    private AlibabaAgentConfig alibabaAgent;
}
```

```java
public class AlibabaAgentConfig {
    private boolean parallelToolExecution = true;
    private int maxParallelTools = 5;
    private boolean returnReasoningContents = true;
    // 后续可扩展：enableLogging、toolExecutionTimeout、outputKey 等
}
```

> **实现注记（2026-06-16）：** 当前仓库的配置模型为 `meta.claw.core.config.VesselConfig`（而非独立的 `VesselMeta`），`agentEngine` 与 `AlibabaAgentConfig` 已作为其字段落地，并通过 `VesselConfigBundle.getAgentEngine()` / `getAlibabaAgentConfig()` 暴露。YAML key 使用 `agent_engine` 与 `alibaba_agent`，由 SnakeYAML 驼峰映射到 Java 字段。

### 6.2 vessel.meta.yaml 示例

```yaml
name: coding-assistant
description: 一个会调用本地 shell、file、git 工具的编程助手
provider: openai
model: gpt-4o
agent_engine: native        # 可切换为 alibaba
alibaba_agent:
  parallel_tool_execution: true
  max_parallel_tools: 5
  return_reasoning_contents: true
memory:
  short_term_store: jsonl
  long_term_store: file
```

---

## 7. 渐进式迁移路线图

| 阶段 | 工作项 | 状态 | 验证标准 |
|------|--------|------|---------|
| **Phase 0** | 引入 `spring-ai-alibaba-agent-framework` / `spring-ai-alibaba-graph-core` 依赖；验证与 Spring AI 1.1.8 的兼容性 | ✅ 已完成 | `./init.sh` 编译通过；新增 AlibabaEngineSmokeTest 能构建 ReactAgent 并调用一次无工具对话 |
| **Phase 1** | 定义 `AgentEngine` SPI；创建 `AgentEngineFactory`；实现 `NativeAgentEngine`；改造 `VesselRuntime` 从 factory 获取引擎 | ✅ 已完成 | `./init.sh` 全量通过；CLI chat 行为与改造前完全一致 |
| **Phase 2** | 实现 `SpringAiAlibabaAgentEngine`（同步 call）；实现 `ReactAgentFactory`；修复 `SpiMessageConverter` tool 消息的 `toolCallId` | ✅ 已完成 | 单个 tool-call 对话用 alibaba 引擎跑通；CLI 可切换 `agent_engine: alibaba` 运行；新增 3 个测试并纳入 P0 基线 |
| **Phase 3** | 接入流式 `streamMessages`；实现 `MetaClawAgentMetricsHook` / `MetaClawModelMetricsHook`；记录 LLM latency / token usage / tool call | ✅ 已完成 | `SpringAiAlibabaAgentEngine.executeStream()` 透传 content/reasoning/tool-call 到 `SpiStreamingCallback`；`ReactAgentFactory` 注册任务级与模型级 Metrics Hook；新增 `SpringAiAlibabaAgentEngineStreamTest`、`MetaClawAgentMetricsHookTest`、`MetaClawModelMetricsHookTest` 并纳入 P0 基线；`./init.sh` 全量通过 |
| **Phase 4** | 实现 `MetaClawHitlHook`，支持 HITL 中断/恢复 | ✅ 已完成 | 新增 `MetaClawHitlHook` 注册到 `ReactAgentFactory`；`SpringAiAlibabaAgentEngine.resume()` 按 `ApprovalResolution` 执行被批准/拒绝的工具并注入结果后再次调用 ReactAgent；新增 `MetaClawHitlHookTest` 与 resume 测试并纳入 P0 基线；`./init.sh` 全量通过 |
| **Phase 5** | 多 Agent 编排：在 `VesselConfig` 中支持子 Agent 配置，把 `SequentialAgent` / `LlmRoutingAgent` 接入 VesselRuntime | 🔄 进行中 | Step 7 配置模型已完成：`AgentFlowMode`、`VesselAgentConfig`、`AgentFlowConfig` 已落地，`VesselConfig` / `VesselConfigBundle` / 配置模板 / `VesselConfigLoaderTest` 已更新；Step 8 SAA 多 Agent 调用接入待实施 |
| **Phase 6**（可选） | 自定义 `VesselCheckpointSaver`：把 SAA thread 状态持久化到 meta-claw MemorySubSystem | ⬜ 未开始 | 进程重启后可从 checkpoint 恢复未完成的 Agent 任务 |

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| Spring AI 1.1.8 与 SAA 1.1.2.3 二进制不兼容 | 启动/运行时 `NoSuchMethodError` | 在 `./init.sh` 中增加 AlibabaEngineSmokeTest；保持 spring-ai-bom 显式导入；出问题时切回 native |
| `ToolResponseMessage.toolCallId` 错误导致 SAA 循环失败 | tool call 结果无法回注，循环异常 | 修复 `SpiMessageConverter`；单测覆盖多 tool-call 场景 |
| SAA Hook 全名唯一性冲突 | 多个 Vessel 同时创建 ReactAgent 时抛异常 | 在 hook name 中拼接 vesselId；或每个 Vessel 使用独立 ReactAgent 实例 |
| 流式 HITL 实现复杂 | Phase 3 延期或行为不一致 | 流式场景下可 fallback 到 native 引擎，直到 SAA 流式 HITL 稳定 |
| Prompt 协议差异 | SAA 的 systemPrompt 与 meta-claw 多子系统变量难以完全对齐 | Phase 1~2 继续由 VesselRuntime 组装完整 messages 传入 ReactAgent；后续再探索把 PromptVars 注入 SAA instruction |
| 引入 SAA 后依赖体积增大 | 启动变慢、类冲突 | 仅引入 `agent-framework` 与 `graph-core`，按需引入 tool starters；监控依赖树 |

---

## 9. 可选深化：工具执行层进一步隔离

### 9.1 问题背景

当前 `AgentExecutor` / `StreamingAgentExecutor` 直接依赖 `org.springframework.ai.tool.ToolCallback`：

```java
import org.springframework.ai.tool.ToolCallback;

private Map<String, ToolCallback> buildToolMap(List<ToolCallback> tools) { ... }
private String executeToolCall(ToolCallback callback, SpiToolCall tc) { ... }
```

虽然 Spring AI `ToolCallback` 已经成为事实标准，但执行层直接引用具体协议会带来两个问题：

1. **执行引擎与工具协议耦合**：未来若引入非 Spring AI 协议的工具（如 Python 远端工具、HTTP 函数、自定义脚本），执行层需要大面积改动。
2. **单元测试成本**：测试 `AgentExecutor` 时必须构造 Spring AI 的 `ToolCallback` / `ToolDefinition`，而不是使用简单的 mock。

### 9.2 目标

让 `AgentExecutor` / `StreamingAgentExecutor` 只依赖 meta-claw 自研的工具执行抽象；Spring AI `ToolCallback` 通过适配器接入。

### 9.3 建议抽象

```java
package meta.claw.core.tool;

/**
 * 对执行引擎暴露的“可执行工具”最小契约。
 *
 * <p>执行引擎只关心：工具叫什么、做什么、怎么调用、返回什么。
 * 具体是本地 Java 方法、Spring AI ToolCallback、MCP Server、HTTP 函数，
 * 由适配层隐藏。</p>
 */
public interface ExecutableTool {

    /** 工具唯一名称，对应 LLM tool_calls 中的 name。 */
    String getName();

    /** 工具自然语言描述，用于注入系统提示。 */
    String getDescription();

    /** 工具输入参数的 JSON Schema（可选，用于本地校验或日志）。 */
    default String getInputSchema() { return null; }

    /**
     * 执行工具。
     *
     * @param argumentsJson LLM 传来的参数 JSON
     * @return 工具结果，建议为 JSON 字符串；执行失败时抛出异常
     */
    String execute(String argumentsJson);
}
```

### 9.4 ToolSubSystem 改造

`ToolSubSystem` 当前返回 `List<ToolCallback>`，改造后返回 `List<ExecutableTool>`：

```java
@Component
public class ToolSubSystem implements VesselSubSystem {

    @Autowired
    private ToolRegistry toolRegistry;
    @Autowired(required = false)
    private List<ToolCallbackProvider> mcpToolProviders;

    /**
     * 获取本 Vessel 可用的所有 ExecutableTool（本地 + MCP）。
     */
    public List<ExecutableTool> getExecutableTools() {
        List<ExecutableTool> all = new ArrayList<>();

        // 本地 @ToolService / @Tool Bean → SpringAiToolCallbackAdapter
        Object[] localBeans = toolRegistry.getToolInstances().toArray();
        if (localBeans.length > 0) {
            for (ToolCallback tc : ToolCallbacks.from(localBeans)) {
                all.add(new SpringAiToolCallbackAdapter(tc));
            }
        }

        // MCP ToolCallbackProvider → SpringAiToolCallbackAdapter
        if (mcpToolProviders != null) {
            for (ToolCallbackProvider provider : mcpToolProviders) {
                for (ToolCallback tc : provider.getToolCallbacks()) {
                    all.add(new SpringAiToolCallbackAdapter(tc));
                }
            }
        }

        return all;
    }

    @Override
    public PromptVars promptVars() {
        List<ExecutableTool> tools = getExecutableTools();
        if (tools.isEmpty()) {
            return PromptVars.empty();
        }
        String toolsText = tools.stream()
            .map(t -> "- " + t.getName() + ": " + t.getDescription())
            .collect(Collectors.joining("\n"));
        return PromptVars.of("tools", toolsText);
    }
}
```

### 9.5 Spring AI 适配器

```java
package meta.claw.core.tool.adapter;

import meta.claw.core.tool.ExecutableTool;
import org.springframework.ai.tool.ToolCallback;

/**
 * 把 Spring AI {@link ToolCallback} 包装为 {@link ExecutableTool}。
 */
public class SpringAiToolCallbackAdapter implements ExecutableTool {

    private final ToolCallback delegate;

    public SpringAiToolCallbackAdapter(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return delegate.getToolDefinition().name();
    }

    @Override
    public String getDescription() {
        return delegate.getToolDefinition().description();
    }

    @Override
    public String getInputSchema() {
        return delegate.getToolDefinition().inputSchema();
    }

    @Override
    public String execute(String argumentsJson) {
        return delegate.call(argumentsJson);
    }

    /**
     * 暴露底层 Spring AI ToolCallback，供 LLM 调用层使用。
     * 当执行层完全解耦后，此方法可由 LlmClientManager 内部消化。
     */
    public Optional<ToolCallback> unwrap() {
        return Optional.of(delegate);
    }

    /**
     * 便捷方法：若传入的是本适配器则解包，否则返回 empty。
     */
    public static Optional<ToolCallback> unwrap(ExecutableTool tool) {
        return tool instanceof SpringAiToolCallbackAdapter adapter
            ? adapter.unwrap()
            : Optional.empty();
    }
}
```

### 9.6 AgentExecutor 改造

```java
@Slf4j
@Component
public class AgentExecutor {

    @Value("${vessel.agent.max-steps:50}")
    private int maxSteps;

    @Autowired
    private LlmClientManager llmClient;

    public Reply execute(TaskContext ctx, SpiChatRequest request) {
        ToolSubSystem toolSub = ctx.getSubSystem("tool");
        HitlSubSystem hitlSub = ctx.getSubSystem("hitl");
        List<ExecutableTool> tools = toolSub != null ? toolSub.getExecutableTools() : List.of();
        Map<String, ExecutableTool> toolMap = buildToolMap(tools);

        // 注意：传给 LlmClientManager 时仍需要 Spring AI ToolCallback，
        // 因此 LlmClientManager 内部再做 ExecutableTool → ToolCallback 的反向适配
        List<ToolCallback> toolCallbacks = tools.stream()
            .map(SpringAiToolCallbackAdapter::unwrap)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());

        List<SpiMessage> messages = new ArrayList<>(request.getMessages());
        return reactLoop(ctx, request, messages, toolCallbacks, toolMap, hitlSub, 1);
    }

    private Map<String, ExecutableTool> buildToolMap(List<ExecutableTool> tools) {
        return tools.stream()
            .collect(Collectors.toMap(ExecutableTool::getName, Function.identity()));
    }

    private String executeToolCall(ExecutableTool tool, SpiToolCall tc) {
        return tool.execute(tc.getArgumentsJson());
    }

    // ...
}
```

> **说明**：`LlmClientManager` 当前直接消费 Spring AI `ToolCallback`，所以 `AgentExecutor` 执行循环内部仍需要把 `ExecutableTool` 转回 `ToolCallback` 传给 LLM。真正的完全解耦需要同步改造 `LlmClientManager` 的接口，让它也接受 `List<ExecutableTool>`，内部再统一适配。这会是一次更大的重构，建议放在 `AgentEngine` SPI 稳定后再做。

### 9.7 收益与代价

| 收益 | 说明 |
|------|------|
| 执行层与 Spring AI 工具协议解耦 | `AgentExecutor` 不再 import `ToolCallback` |
| 单测更简单 | 可以用 lambda / mock 直接构造 `ExecutableTool` |
| 支持非 Spring AI 工具 | 例如 HTTP 函数、Python 远端工具，只需新增适配器 |
| HITL 策略更纯粹 | `HitlSubSystem` 评估时也只依赖 `ExecutableTool`，不依赖具体协议 |

| 代价 | 说明 |
|------|------|
| 多一层转换 | `ToolCallback` → `ExecutableTool` → `ToolCallback`，有轻微运行时开销 |
| 需要同步改 `LlmClientManager` | 否则执行循环里仍要做反向适配 |
| 当前收益有限 | 因为工具协议短期内仍是 Spring AI |

### 9.8 实施建议

- **短期（AgentEngine SPI 阶段）**：不改。优先保证执行引擎抽象落地，避免同时改两条线。
- **中期（Alibaba 引擎稳定后）**：把 `ToolSubSystem.getToolCallbacks()` 改为 `getExecutableTools()`，`AgentExecutor` / `StreamingAgentExecutor` 同步改造。
- **长期**：`LlmClientManager` 也接受 `List<ExecutableTool>`，彻底消灭执行层对 `org.springframework.ai.tool.ToolCallback` 的依赖。

---

## 10. 依赖变更

在 `meta-claw-core/pom.xml` 中新增：

```xml
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-agent-framework</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud.ai</groupId>
    <artifactId>spring-ai-alibaba-graph-core</artifactId>
</dependency>
```

> 注意：SAA 的 tool-calling starters 不在其 BOM 中，仍需显式版本。当前 `spring-ai-alibaba-bom` 已导入，上述两个核心模块版本由 BOM 统一管理。

---

## 11. 接口与实现类清单

| 类别 | 类/接口 | 所属包 | 状态 | 说明 |
|------|---------|--------|------|------|
| SPI | `AgentEngine` | `meta.claw.core.runtime.engine` | ✅ 已完成 | Phase 1 落地 |
| Factory | `AgentEngineFactory` | `meta.claw.core.runtime.engine` | ✅ 已完成 | Phase 1 落地 |
| 实现 | `NativeAgentEngine` | `meta.claw.core.runtime.engine` | ✅ 已完成 | Phase 1 落地，复用现有执行器 |
| 实现 | `SpringAiAlibabaAgentEngine` | `meta.claw.core.runtime.engine` | ✅ 已完成 | Phase 2 落地 |
| Factory | `ReactAgentFactory` | `meta.claw.core.runtime.engine` | ✅ 已完成 | Phase 2 落地 |
| Converter | `SpiMessageConverter` | `meta.claw.core.runtime.engine` | ✅ 已完成 | Phase 2 落地，已修复 toolCallId |
| Hook | `MetaClawHitlHook` | `meta.claw.core.runtime.engine.alibabahook` | ✅ 已完成 | Phase 4 落地，注册到 `ReactAgentFactory` 的 `AFTER_MODEL` 位置 |
| Hook | `MetaClawAgentMetricsHook` | `meta.claw.core.runtime.engine.alibabahook` | ✅ 已完成 | Phase 3 落地，任务级指标（任务完成/步数/时长） |
| Hook | `MetaClawModelMetricsHook` | `meta.claw.core.runtime.engine.alibabahook` | ✅ 已完成 | Phase 3 落地，模型级指标（LLM latency/token usage/tool calls） |
| 修改 | `VesselRuntime` | `meta.claw.core.runtime` | ✅ 已完成 | Phase 1 落地，注入 AgentEngineFactory |
| 修改 | `VesselConfig` / `AlibabaAgentConfig` | `meta.claw.core.config` | ✅ 已完成 | Phase 1 落地，新增配置字段 |
| 新增 | `VesselAgentConfig` / `AgentFlowConfig` / `AgentFlowMode` | `meta.claw.core.config` | ✅ 已完成 | Phase 5 Step 7 落地，多 Agent 配置模型 |
| 修改 | `LlmClientProviderManager` | `meta.claw.core.llm` | ✅ 已完成 | Phase 2 落地，新增 `createChatModel` |

### 11.2 可选工具抽象隔离新增/修改清单

| 类别 | 类/接口 | 所属包 | 说明 |
|------|---------|--------|------|
| SPI | `ExecutableTool` | `meta.claw.core.tool` | 新增，执行引擎面向的最小工具契约 |
| Adapter | `SpringAiToolCallbackAdapter` | `meta.claw.core.tool.adapter` | 新增，Spring AI ToolCallback → ExecutableTool |
| 修改 | `ToolSubSystem` | `meta.claw.core.runtime.subsystem` | `getToolCallbacks()` 改为 `getExecutableTools()` |
| 修改 | `AgentExecutor` / `StreamingAgentExecutor` | `meta.claw.core.runtime` | 只依赖 `ExecutableTool` |
| 修改（长期） | `LlmClientManager` | `meta.claw.core.llm` | 接受 `List<ExecutableTool>`，内部统一适配 |

---

## 12. 结论

- **meta-claw 当前自研执行模型已经能支撑单 Agent、工具循环、HITL、Skill、Metrics 等核心能力**，但多 Agent 编排和状态机恢复是明显短板。
- **Spring AI Alibaba 的 ReactAgent/Graph 能补齐这些短板**，但直接替换会浪费现有投资并引入版本风险。
- **推荐方案是通过 `AgentEngine` SPI 把执行引擎抽象出来，提供 `native` 和 `alibaba` 两种可切换实现**，底层统一使用 Spring AI。
- **实施顺序**：先验证依赖兼容性 → 抽 SPI + 本地实现兜底 → 接入 Alibaba 同步引擎 → 流式 → HITL → 多 Agent → checkpoint 持久化。
- **每阶段都必须通过 `./init.sh` 和新增 smoke test 验证**，确保仓库始终保持可启动、可验证状态。

---

## 13. 参考文档

- [Spring AI Alibaba GitHub](https://github.com/alibaba/spring-ai-alibaba)
- [Spring AI Alibaba 官方文档](https://java2ai.com)
- meta-claw 内部：`2026-05-22-vessel-runtime-evolution-design.md`
- meta-claw 内部：`2026-05-30-prompt-architecture-redesign.md`
- meta-claw 内部：`feature_list.json`（当前最高优先级功能记录）
