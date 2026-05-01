# Meta-Claw AI Agent Platform 架构设计文档

> 基于 expert_project（Python/kosong）核心能力复刻，参考 agents-flex 模块划分，采用 Spring Boot + Spring AI 技术栈。

---

## 1. 概述

本文档定义 Meta-Claw 项目中 AI Agent 平台的完整架构蓝图。目标是将现有 Python 项目 expert_project 的核心能力（多专家管理、工具调用、记忆、知识库、CLI 交互）迁移到 Java 生态，同时参考 agents-flex 的模块化设计理念。

**关键约束：**
- 与现有 Spring Boot 3.2 + Spring AI 0.8.0 技术栈统一
- 所有新增模块命名统一为 `meta-claw-xxx`
- 实现分阶段推进，每阶段独立可运行

---

## 2. 模块划分

### 2.1 现有模块调整

```text
meta_claw/
├── meta-claw-core/                 ← 合并原 core + 原 runtime
│   ├── eventbus/                   ← 已有：EventBus、事件定义
│   ├── model/                      ← 已有：Reply、Context、事件模型
│   ├── runtime/                    ← 迁入：ExpertRuntime、AgentLoop、ExpertManager
│   └── spi/                        ← 新增：LlmClient、Message、ToolCall 等抽象
│
├── meta-claw-session/              ← 已有：会话管理
├── meta-claw-gateway/              ← 已有：Gateway、ChatChannel、ChatMessage
├── meta-claw-gateway-weixin/       ← 已有：微信渠道（已接入 openilink-sdk-java）
└── meta-claw-bootstrap/            ← 已有：Spring Boot 入口
```

### 2.2 新增模块

| 模块 | 职责 | 参考来源 |
|------|------|----------|
| `meta-claw-export` | 专家/分身 YAML 配置管理、导入导出 | expert_project 的 expert 目录结构 |
| `meta-claw-store` | Memory（JSONL 会话存储）+ Knowledge（Git-backed Markdown） | expert_project `memory/` + `knowledge/` |
| `meta-claw-tool` | 工具引擎：方法反射扫描、JSON Schema 生成、本地执行、审批流 | expert_project `ToolRegistry` + `ToolExecutor` |
| `meta-claw-mcp` | MCP 客户端：连接 MCP Server、工具发现、调用 | agents-flex MCP 模块 + MCP 协议 |
| `meta-claw-skill` | 技能系统：文件系统热加载、动态注册卸载 | expert_project `default_skills` |
| `meta-claw-cli` | CLI 交互层：picocli、交互模式、后台 daemon、TUI | expert_cli `cli.py` + `app.py` |

### 2.3 依赖关系

```
meta-claw-bootstrap
    ├── meta-claw-gateway-weixin
    │   ├── meta-claw-gateway
    │   │   └── meta-claw-core
    │   └── meta-claw-session
    │       └── meta-claw-core
    │
    ├── meta-claw-cli
    │   ├── meta-claw-export
    │   ├── meta-claw-skill
    │   │   └── meta-claw-tool
    │   │       └── meta-claw-core
    │   └── meta-claw-mcp
    │       └── meta-claw-core
    │
    └── meta-claw-store
        └── meta-claw-core
```

**设计原则：**
- `meta-claw-core` 是唯一的核心引擎层，所有新增模块只依赖它
- 模块间通过 `EventBus` 解耦，避免直接交叉依赖
- `meta-claw-tool`、`meta-claw-mcp`、`meta-claw-skill` 最终都向 `meta-claw-core` 暴露统一的 `ToolDefinition`

---

## 3. 核心接口设计（meta-claw-core SPI）

### 3.1 LlmClient — 统一 LLM 调用

```java
public interface LlmClient {
    ChatResponse chat(ChatRequest request);
    void chatStream(ChatRequest request, StreamingCallback callback);
    CompletableFuture<ChatResponse> chatAsync(ChatRequest request);
    ProviderMeta getProviderMeta();
}

public record ProviderMeta(String name, String model, String baseUrl) {}
```

**实现类：** `SpringAiLlmClient` — 包装 Spring AI 1.x 的 `ChatClient`，通过配置切换 Provider（Kimi/OpenAI/Ollama）。

### 3.2 消息与工具模型

```java
public record ChatRequest(
    List<Message> messages,
    List<ToolDefinition> tools,
    Map<String, Object> options
) {}

public record Message(
    String role,        // system / user / assistant / tool
    String content,
    List<ToolCall> toolCalls
) {}

public record ToolDefinition(
    String name,
    String description,
    JsonSchema parameters
) {}

public record ToolCall(
    String id,
    String name,
    Map<String, Object> arguments
) {}

public record ToolResult(
    String toolCallId,
    boolean success,
    String content,
    String errorMessage
) {}

public record ChatResponse(
    String content,
    List<ToolCall> toolCalls,
    Usage usage,
    Map<String, Object> metadata
) {}
```

### 3.3 流式回调

```java
public interface StreamingCallback {
    void onStart();
    void onChunk(String chunk);
    void onToolCall(ToolCall toolCall);
    void onComplete(ChatResponse response);
    void onError(Throwable error);
}
```

### 3.4 事件总线（已有，扩展）

`meta.claw.core.eventbus.EventBus` 已存在，新增事件类型：

| 事件 | 发布者 | 订阅者 | 说明 |
|------|--------|--------|------|
| `UserMessageReceived` | Gateway / CLI | AgentLoop | 用户消息到达 |
| `ExpertResponseReady` | ExpertRuntime | Gateway / CLI | 专家响应就绪 |
| `ToolExecutionRequested` | ExpertRuntime | ToolExecutor | 工具执行请求 |
| `ToolExecutionCompleted` | ToolExecutor | ExpertRuntime | 工具执行完成 |
| `ToolApprovalRequested` | ToolExecutor | CLI / Gateway | 需要用户审批 |
| `ToolApproved` | CLI / Gateway | ToolExecutor | 用户确认执行 |

### 3.5 ExpertRuntime — 运行时编排

```java
public class ExpertRuntime {
    // 同步/流式/异步入口
    public ChatResponse chat(Session session, String userInput);
    public void chatStream(Session session, String userInput, StreamingCallback callback);
    public CompletableFuture<ChatResponse> chatAsync(Session session, String userInput);

    // Prompt 组装：expert config + skills + knowledge + memory
    private String buildSystemPrompt(ExpertConfig config);

    // Tool 调用循环：调用 LLM → 解析 ToolCall → 执行 → 回注 → 再次调用
    private ChatResponse chatWithTools(ChatRequest request);
}
```

---

## 4. 数据流：一次完整的消息生命周期

以 **CLI 交互模式**为例，用户输入 `"查一下北京天气"`：

```
1. User Input → meta-claw-cli（解析命令，若非命令则发布事件）
2. EventBus.publish(UserMessageReceived) → AgentLoop 订阅处理
3. AgentLoop → SessionManager.getSession() 获取当前会话
4. AgentLoop → ExpertRuntime.chat()
5. ExpertRuntime.buildSystemPrompt() 组装：
      expert-config.yaml（角色、目标）
    + meta-claw-skill（已加载技能描述）
    + meta-claw-store/knowledge（检索到的相关知识）
    + meta-claw-store/memory（最近 N 轮对话上下文）
6. ExpertRuntime → SpringAiLlmClient.chat() → Kimi API
7. LLM 返回 ToolCall（如 weather_search）
8. ExpertRuntime → CompositeToolRegistry 路由工具来源：
      本地工具 → meta-claw-tool
      MCP 工具 → meta-claw-mcp
      技能方法 → meta-claw-skill
9. meta-claw-tool/ToolExecutor.execute()：
      若需审批：挂起 → 发布 ToolApprovalRequested → CLI 渲染确认 → 用户确认后发布 ToolApproved → 继续执行
      若免审批：直接反射调用 → 封装 ToolResult
10. ToolResult 追加到 messages，再次调用 LLM（循环 6→10）
11. LLM 返回最终文本 → ExpertRuntime 发布 ExpertResponseReady
12. meta-claw-cli/StreamHandler 输出到终端
13. meta-claw-store/MemoryManager 异步持久化到 JSONL
```

---

## 5. 分阶段实现路线图

### Phase 1：基础骨架（MVP）

**目标**：CLI 配置 Kimi API Key 后，能进行多轮流式对话。

| 模块 | 内容 |
|------|------|
| **meta-claw-core** | 合并原 runtime；新增 `LlmClient` SPI、`Message`/`ChatRequest`/`ChatResponse` 模型、`StreamingCallback`；实现 `SpringAiLlmClient`（包装 Spring AI ChatClient，支持 Kimi Provider） |
| **meta-claw-export** | `ExpertConfig` YAML 定义（name、systemPrompt、model、provider、apiKeyPath）；`ExpertConfigLoader`；默认专家模板 |
| **meta-claw-cli** | picocli 骨架；`config set apiKey`；`chat <expert>` 交互命令（prompt_toolkit 风格）；流式输出到终端；`/exit`、`/clear` 内联命令 |

**验收标准**：`meta-claw chat default` 能配置 Key 并与 Kimi 对话，流式输出正常。

---

### Phase 2：知识库 + 后台模式 + 观测性

**目标**：专家基于知识库回答领域问题；CLI 支持后台守护进程；具备可观测能力。

| 模块 | 内容 |
|------|------|
| **meta-claw-store/knowledge** | `KnowledgeManager`：Git-backed Markdown 存储、YAML frontmatter 解析（`KnowledgeEntry`）、关键词检索、`KnowledgeAnalyzer`（LLM 辅助分析）、矛盾检测（旧事实标记 SUPERSEDED）、分支实验（`branch/checkout/merge`） |
| **meta-claw-core** | `ExpertRuntime` Prompt 组装注入 Knowledge 上下文；`KnowledgeRetriever` 接口 |
| **meta-claw-cli** | `serve` / `start` / `stop` 命令：后台 daemon（`Popen`/`ProcessBuilder` 模式，PID 文件管理）；`send` 命令（向后台进程发消息）；`status` 查看运行状态 |
| **Observability** | Micrometer Metrics（LLM 调用延迟、Token 消耗、工具成功率）；OpenTelemetry Tracing（分布式追踪链路）；结构化日志（JSON 格式，SLF4J + Logback） |

**验收标准**：专家能基于知识库文件回答专业问题；`meta-claw start` 后台运行，`meta-claw send` 能向后台发消息；日志输出 JSON 格式。

---

### Phase 3：工具引擎 + 记忆

**目标**：支持本地工具调用、对话记忆、审批流。

| 模块 | 内容 |
|------|------|
| **meta-claw-tool** | `ToolRegistry`：方法反射扫描（`@Tool` 注解）、`JsonSchemaGenerator`（从 Java 类型生成 JSON Schema）、`ToolExecutor`（本地反射执行、超时控制、异常隔离）、并行执行（Semaphore 限流）、审批流（`approvalRequired` 配置） |
| **meta-claw-store/memory** | `MemoryManager`：JSONL 会话文件读写、`SessionStorage`、上下文窗口截断策略（最近 N 轮 / Token 限制）、事实提取（规则-based，可扩展 LLM-based） |
| **meta-claw-core** | `ExpertRuntime` 集成：Prompt 组装注入 Memory；LLM 返回 ToolCall 时路由到 `ToolExecutor`；工具结果回注消息历史并循环调用 |
| **meta-claw-cli** | 审批流交互：工具调用前暂停，终端渲染确认提示（Y/n），支持批量审批（并行工具时汇总所有待审批项） |

**验收标准**：对话中 LLM 能调用本地工具（如文件读取、计算）；多轮对话记住上下文；危险工具执行前要求确认。

---

### Phase 4：MCP 客户端 + 技能系统

**目标**：支持外部 MCP Server 和动态技能加载。

| 模块 | 内容 |
|------|------|
| **meta-claw-mcp** | `McpClient`：stdio / SSE 传输、初始化（`initialize`）、工具发现（`tools/list`）、工具调用（`tools/call`）、将 MCP 工具适配为 `ToolDefinition` |
| **meta-claw-skill** | `SkillLoader`：文件系统监控（WatchService）热加载；`SkillRegistry`：注册/卸载；技能包格式（`skill.yaml` + 脚本/类文件），参考 expert_project `default_skills` 结构 |
| **meta-claw-core** | `CompositeToolRegistry`：统一聚合 local tools（meta-claw-tool）+ mcp tools（meta-claw-mcp）+ skill methods（meta-claw-skill），对外提供统一 `List<ToolDefinition>` |

**验收标准**：能连接外部 MCP Server（如 filesystem、fetch）；技能目录文件变动后自动热加载。

---

## 6. 技术栈

| 层级 | 技术选型 |
|------|----------|
| 基础框架 | Spring Boot 3.2、Spring AI 1.x（升级） |
| LLM 调用 | Spring AI ChatClient（包装为 `LlmClient` SPI） |
| CLI | picocli + JLine（或 prompt_toolkit 的 Java 等价物） |
| 序列化 | Jackson 2.18 |
| 日志 | SLF4J 2.0 + Logback 1.5（JSON 结构化输出） |
| 观测性 | Micrometer + OpenTelemetry |
| 测试 | JUnit 5.11 + Mockito 5.15 |
| 构建 | Maven 多模块 |

---

## 7. 风险与假设

| 风险 | 缓解措施 |
|------|----------|
| Spring AI 1.x API 变动 | 通过 `LlmClient` SPI 隔离，底层实现可替换 |
| MCP 协议版本不兼容 | 参考官方 `mcp-sdk-java`，协议层独立封装 |
| 知识库 Git 操作冲突 | 采用文件级锁或 Git 工作树隔离 |
| 后台 daemon 跨平台差异 | macOS 用 `launchd`，Linux 用 `systemd`，Windows 用服务包装器；优先支持 macOS/Linux |

---

*文档版本：v1.0*  
*日期：2026-04-30*
