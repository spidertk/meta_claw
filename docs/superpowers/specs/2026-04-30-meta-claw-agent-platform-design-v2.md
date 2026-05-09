# Meta-Claw AI Agent Platform 架构设计文档（v2）

> 基于 expert_project（Python/kosong）核心能力复刻，参考 agents-flex 模块划分，采用 Spring Boot + Spring AI 技术栈。  
> **v2 变更**：统一抽象为 Vessel（数字员工）模型，重构配置目录结构，模块 `meta-claw-export` 更名为 `meta-claw-vessel`；`meta-claw-session` 合并入 `meta-claw-core`，新增 `meta-claw-store` 负责数据持久化。

---

## 1. 概述

本文档定义 Meta-Claw 项目中 AI Agent 平台的完整架构蓝图。目标是将现有 Python 项目 expert_project 的核心能力（多专家管理、工具调用、记忆、知识库、CLI 交互）迁移到 Java 生态，同时参考 agents-flex 的模块化设计理念。

**关键约束：**
- 与现有 Spring Boot 3.2.5 + Spring AI 1.1.4 技术栈统一
- 所有新增模块命名统一为 `meta-claw-xxx`
- 实现分阶段推进，每阶段独立可运行
- **v2 新增**：统一使用 Vessel（数字员工）作为核心抽象概念，替代原有的 Expert
- **v2 新增**：`meta-claw-session` 模块功能迁入 `meta-claw-core`，会话实现下沉至 `meta-claw-store`
- **v2 新增**：引入 Prompt Engineering 模块，系统模板打包至 `meta-claw-core`，后续对接百炼平台

---

## 2. 用户数据目录结构

`meta-cli init` 初始化后在 `~/.meta-claw/` 创建以下目录和文件：

```text
~/.meta-claw/
├── config.yaml              # 主配置文件（providers、default_provider 等）
├── vessels/                 # 数字员工目录
│   └── default/
│       ├── vessel.md        # Vessel 定义（名称、角色、目标、领域知识）
│       ├── config.yaml      # Agent 配置（可选，覆盖全局 provider）
│       ├── templates/       # Vessel 级 prompt 模板覆盖（可选）
│       ├── skills/          # 私有 Skill 文件
│       │   └── *.md
│       ├── knowledge/       # 私有领域知识库文件
│       │   └── topic/
│       │       └── knowledge.md
│       ├── conversations/   # 对话历史存储（JSONL）
│       │   └── {sessionKey}/
│       │       ├── history.jsonl
│       │       └── media/
│       └── preferences/     # 用户偏好/习惯存储（JSONL）
│           └── preferences.jsonl
└── skills/                  # 系统技能目录（全局共享）
    └── *.md
```

### 2.1 主配置文件 `config.yaml`

```yaml
default_provider: moonshot
providers:
  moonshot:
    api_key: "your-api-key"
    base_url: "https://api.moonshot.cn/"
    model: "kimi-k2.5"
    temperature: 1
    timeout: 60.0
```

### 2.2 Vessel 定义文件 `vessel.md`

```yaml
---
id: default
name: Default Vessel
description: A general-purpose AI assistant
emoji: 🤖
model: kimi-k2.5              # 可覆盖全局配置
system_prompt: |
  You are a helpful AI assistant...
preferences_enabled: true
knowledge_dir: knowledge
---
```

### 2.3 Vessel 配置 `config.yaml`（可选）

```yaml
provider: moonshot            # 指定使用哪个 provider
model: kimi-k2.5              # 可覆盖 provider 默认 model
max_history: 20               # 上下文窗口轮数
temperature: 0.8              # 可覆盖 provider 默认 temperature
```

---

## 3. 模块划分

### 3.1 现有模块调整

```text
meta_claw/
├── meta-claw-core/                 ← 合并原 core + 原 runtime + 原 session
│   ├── eventbus/                   ← 已有：EventBus、事件定义
│   ├── model/                      ← 已有：Reply、Context、事件模型
│   ├── runtime/                    ← 已有：AgentLoop、ExpertManager、ExpertRuntime
│   ├── session/                    ← 迁入：SessionManager、UserSession、SessionStorage、ConversationStore、UserPreferenceStore
│   ├── prompt/                     ← 新增：PromptContext、SystemPromptBuilder、TemplateLoader
│   └── spi/                        ← 已有：SpiLlmClient、SpiMessage、SpiToolCall 等抽象
│
├── meta-claw-gateway/              ← 已有：Gateway、ChatChannel、ChatMessage
├── meta-claw-gateway-weixin/       ← 已有：微信渠道（已接入 openilink-sdk-java）
├── meta-claw-bootstrap/            ← 已有：Spring Boot 入口
├── meta-claw-vessel/               ← 已有：VesselConfig、VesselConfigLoader、GlobalConfigLoader
└── meta-claw-cli/                  ← 已有：picocli 骨架、交互命令
```

### 3.2 新增/更名模块

| 模块 | 职责 | 参考来源 |
|------|------|----------|
| `meta-claw-vessel` | Vessel（数字员工）YAML 配置管理、模板生成、目录结构初始化、GlobalConfig 加载 | **v2 由 `meta-claw-export` 更名**；expert_project 的 expert 目录结构 |
| `meta-claw-store` | Conversation（JSONL 对话历史）+ Preferences（JSONL 用户偏好）+ Knowledge（Git-backed Markdown，预留） | expert_project `memory/` + `knowledge/` |
| `meta-claw-tool` | 工具引擎：方法反射扫描、JSON Schema 生成、本地执行、审批流 | expert_project `ToolRegistry` + `ToolExecutor` |
| `meta-claw-mcp` | MCP 客户端：连接 MCP Server、工具发现、调用 | agents-flex MCP 模块 + MCP 协议 |
| `meta-claw-skill` | 技能系统：文件系统热加载、动态注册卸载 | expert_project `default_skills` |
| `meta-claw-cli` | CLI 交互层：picocli、交互模式、后台 daemon、TUI | expert_cli `cli.py` + `app.py` |

### 3.3 依赖关系

```
meta-claw-bootstrap
    ├── meta-claw-gateway-weixin
    │   ├── meta-claw-gateway
    │   │   └── meta-claw-core
    │   └── meta-claw-store          # 可选：如果需要文件存储
    │       └── meta-claw-core
    │
    ├── meta-claw-cli
    │   ├── meta-claw-vessel
    │   ├── meta-claw-skill
    │   │   └── meta-claw-tool
    │   │       └── meta-claw-core
    │   └── meta-claw-mcp
    │       └── meta-claw-core
    │
    └── meta-claw-store
        └── meta-claw-core
```

---

## 4. 核心接口设计（meta-claw-core SPI）

### 4.1 SpiLlmClient — 统一 LLM 调用

```java
public interface SpiLlmClient {
    SpiChatResponse chat(SpiChatRequest request);
    void chatStream(SpiChatRequest request, SpiStreamingCallback callback);
    CompletableFuture<SpiChatResponse> chatAsync(SpiChatRequest request);
    SpiProviderMeta getProviderMeta();
}

public record SpiProviderMeta(String name, String model, String baseUrl) {}
```

**实现类：** `SpringAiLlmClient` — 包装 Spring AI 1.1.4 的 `ChatClient`，通过配置切换 Provider（Kimi/OpenAI/Ollama）。

### 4.2 消息与工具模型（v2：统一 Spi 前缀）

```java
@Builder
public record SpiChatRequest(
    List<SpiMessage> messages,
    List<SpiToolDefinition> tools,
    Map<String, Object> options
) {}

@Builder
public record SpiMessage(
    String role,        // system / user / assistant / tool
    String content,
    List<SpiToolCall> toolCalls
) {
    public static SpiMessage system(String content) { ... }
    public static SpiMessage user(String content) { ... }
    public static SpiMessage assistant(String content) { ... }
    public static SpiMessage tool(String content) { ... }
}

@Builder
public record SpiToolDefinition(
    String name,
    String description,
    SpiJsonSchema parameters
) {}

@Builder
public record SpiToolCall(
    String id,
    String name,
    Map<String, Object> arguments
) {}

@Builder
public record SpiToolResult(
    String toolCallId,
    boolean success,
    String content,
    String errorMessage
) {}

@Builder
public record SpiChatResponse(
    String content,
    List<SpiToolCall> toolCalls,
    SpiUsage usage,
    Map<String, Object> metadata
) {}

@Builder
public record SpiUsage(
    Integer promptTokens,
    Integer completionTokens,
    Integer totalTokens
) {}
```

### 4.3 流式回调

```java
public interface SpiStreamingCallback {
    void onStart();
    void onChunk(String chunk);
    void onToolCall(SpiToolCall toolCall);
    void onComplete(SpiChatResponse response);
    void onError(Throwable error);
}
```

### 4.4 事件总线（已有，扩展）

| 事件 | 发布者 | 订阅者 | 说明 |
|------|--------|--------|------|
| `UserMessageReceived` | Gateway / CLI | AgentLoop | 用户消息到达 |
| `VesselResponseReady` | VesselRuntime | Gateway / CLI | Vessel 响应就绪 |
| `ToolExecutionRequested` | VesselRuntime | ToolExecutor | 工具执行请求 |
| `ToolExecutionCompleted` | ToolExecutor | VesselRuntime | 工具执行完成 |
| `ToolApprovalRequested` | ToolExecutor | CLI / Gateway | 需要用户审批 |
| `ToolApproved` | CLI / Gateway | ToolExecutor | 用户确认执行 |

### 4.5 会话管理 SPI（meta-claw-core/session）

```java
// 会话状态存储（轻量级：mode、targetExpert、timeout 等）
public interface SessionStorage {
    UserSession get(String sessionKey);
    void save(UserSession session);
    void delete(String sessionKey);
    List<UserSession> listAll();
    void cleanupExpired(long maxInactiveMinutes);
}

// 对话历史存储（消息追加读写）
public interface ConversationStore {
    void appendMessage(String sessionKey, ChatMessage message);
    List<ChatMessage> getHistory(String sessionKey, int limit);
    List<ConversationInfo> listConversations();
    boolean clearHistory(String sessionKey);
    boolean deleteConversation(String sessionKey);
    boolean conversationExists(String sessionKey);
}

// 用户偏好存储（非领域知识：偏好、习惯、工具使用模式）
public interface UserPreferenceStore {
    void addPreference(String vesselId, PreferenceEntry entry);
    List<PreferenceEntry> lookupPreference(String vesselId, String query);
    List<PreferenceEntry> listRecentPreferences(String vesselId, int limit);
    boolean deletePreference(String vesselId, String preferenceId);
    boolean clearPreferences(String vesselId);
}
```

**核心 DTO：**
```java
@Builder @Getter @Setter
public class UserSession {
    private String sessionKey;      // userId:source:agentId
    private String userId;
    private String source;
    private String agentId;
    private ChatMode mode;          // SINGLE / GROUP
    private String targetExpert;
    private String groupSessionId;
    private boolean debugMode;
    private LocalDateTime lastActivity;
    private LocalDateTime createdAt;
}

@Builder @Getter @Setter
public class ChatMessage {
    private String messageId;
    private String sessionKey;
    private String role;            // system / user / assistant / tool
    private String content;
    private String messageType;
    private String userId;
    private String username;
    private String replyTo;
    private String expertName;
    private LocalDateTime timestamp;
    private Map<String, Object> metadata;
}

@Builder @Getter @Setter
public class PreferenceEntry {
    private String id;
    private LocalDateTime timestamp;
    private String category;        // preference / fact / tool_usage / context
    private String content;
    private Map<String, Object> metadata;
}
```

### 4.6 Prompt Engineering（meta-claw-core/prompt）

```java
// Prompt 上下文：组装 system prompt 所需的全部数据
public class PromptContext {
    private ExpertIdentity identity;
    private String expertContext;        // vessel.md 原始内容
    private String knowledgeContext;     // 静态领域知识
    private String preferencesContext;   // 用户偏好/习惯（NOT domain knowledge）
    private List<SkillInfo> skills;
    private List<ToolInfo> tools;
    private Path workspaceDir;
    private Map<String, Object> runtimeInfo;
    private String currentTime;
    private String location;
}

// System Prompt 构建器
public class SystemPromptBuilder {
    public String build(PromptContext context);
}

// 模板加载器：从 classpath 或文件系统加载 .tmpl.md
public class TemplateLoader {
    public String loadSystemTemplate();
    public String loadContextTemplate();
    public String loadVesselTemplate();
}
```

**模板体系：**
- `meta-claw-core/src/main/resources/templates/system.tmpl.md`：系统指令主模板（身份、工具、技能、安全规则、Knowledge vs Preferences 使用指南）
- `meta-claw-core/src/main/resources/templates/context.tmpl.md`：动态上下文模板（workspace、runtime、knowledge、preferences、时间地点）
- `meta-claw-vessel/src/main/resources/templates/vessel.tmpl.md`：Vessel 定义模板（YAML frontmatter + Identity/Soul/Domain Knowledge/Capabilities/Guidelines/Preferences）

**Vessel 级模板覆盖（可选）：** `vessels/{vesselId}/templates/` 可覆盖系统模板。

### 4.7 VesselRuntime — 运行时编排

```java
public class VesselRuntime {
    // 同步/流式/异步入口
    public SpiChatResponse chat(UserSession session, String userInput);
    public void chatStream(UserSession session, String userInput, SpiStreamingCallback callback);
    public CompletableFuture<SpiChatResponse> chatAsync(UserSession session, String userInput);

    // Prompt 组装：vessel config + skills + knowledge + preferences
    private String buildSystemPrompt(VesselConfig config);

    // Tool 调用循环：调用 LLM → 解析 SpiToolCall → 执行 → 回注 → 再次调用
    private SpiChatResponse chatWithTools(SpiChatRequest request);
}
```

---

## 5. 数据流：一次完整的消息生命周期

以 **CLI 交互模式**为例，用户输入 `"查一下北京天气"`：

```
1. User Input → meta-claw-cli（解析命令，若非命令则发布事件）
2. EventBus.publish(UserMessageReceived) → AgentLoop 订阅处理
3. AgentLoop → SessionManager.getSession() 获取当前会话状态（UserSession: mode, targetExpert）
4. AgentLoop → VesselRuntime.chat()
5. VesselRuntime.buildSystemPrompt() 组装：
      vessel.md（Identity、Soul、Domain Knowledge）
    + system.tmpl.md（工具、技能、安全规则、Knowledge vs Preferences 使用指南）
    + context.tmpl.md：
        - workspace_section（工作目录结构）
        - runtime_section（运行时信息）
        - knowledge_section（meta-claw-store/knowledge 检索到的领域知识）
        - preferences_section（meta-claw-store/preferences 检索到的用户偏好/习惯）
        - current_time / location
6. VesselRuntime → SpringAiLlmClient.chat() → Kimi API
7. LLM 返回 SpiToolCall（如 weather_search）
8. VesselRuntime → CompositeToolRegistry 路由工具来源：
      本地工具 → meta-claw-tool
      MCP 工具 → meta-claw-mcp
      技能方法 → meta-claw-skill
9. meta-claw-tool/ToolExecutor.execute()：
      若需审批：挂起 → 发布 ToolApprovalRequested → CLI 渲染确认 → 用户确认后发布 ToolApproved → 继续执行
      若免审批：直接反射调用 → 封装 SpiToolResult
10. SpiToolResult 追加到 messages，再次调用 LLM（循环 6→10）
11. LLM 返回最终文本 → VesselRuntime 发布 VesselResponseReady
12. meta-claw-cli/StreamHandler 输出到终端
13. meta-claw-store/conversation 异步追加消息到 history.jsonl
```

---

## 6. 分阶段实现路线图

### Phase 1：基础骨架（MVP）✅ 已完成

**目标**：CLI 配置 Kimi API Key 后，能进行多轮流式对话。

| 模块 | 内容 |
|------|------|
| **meta-claw-core** | 合并原 runtime；新增 `SpiLlmClient` SPI、`SpiMessage`/`SpiChatRequest`/`SpiChatResponse` 模型、`SpiStreamingCallback`；实现 `SpringAiLlmClient`（包装 Spring AI ChatClient，支持 Kimi Provider） |
| **meta-claw-vessel** | **v2 更名（原 `meta-claw-export`）**：`VesselConfig` YAML 定义（vessel.md + config.yaml）；`VesselConfigLoader`；`GlobalConfigLoader`（读取 `~/.meta-claw/config.yaml`）；初始化目录结构（`vessels/`、`skills/`） |
| **meta-claw-cli** | picocli 骨架；`init` 命令（创建 `~/.meta-claw/` 目录结构和默认 vessel）；`config set/get/list` 命令（操作 `~/.meta-claw/config.yaml`）；`chat <vessel>` 交互命令；流式输出到终端；`/exit`、`/clear` 内联命令 |

**验收标准**：
- `meta-cli init` 创建 `~/.meta-claw/config.yaml` + `vessels/default/` + `skills/`
- `meta-cli chat default` 能读取 `config.yaml` 中的 provider 配置并与 Kimi 对话
- 流式输出正常

### Phase 2：会话迁移 + 存储层 + Prompt 工程（当前）

**目标**：会话管理迁入 core，新增 store 持久化层，建立 Prompt Engineering 框架。

| 模块 | 内容 |
|------|------|
| **meta-claw-core/session** | 迁入原 `meta-claw-session` 全部功能；新增 `ConversationStore`、`UserPreferenceStore` 存储 SPI；`ChatMessage`、`PreferenceEntry`、`ConversationInfo` DTO |
| **meta-claw-store/conversation** | `JsonlConversationStore`：基于 JSONL 追加写法的对话历史存储，线程安全（按 session 读写锁），支持 media 文件存储 |
| **meta-claw-store/preferences** | `FilePreferenceStore`：基于 JSONL 的用户偏好存储，category 支持 preference/fact/tool_usage/context |
| **meta-claw-core/prompt** | `PromptContext`、`SystemPromptBuilder`、`TemplateLoader`；系统模板 `system.tmpl.md` + `context.tmpl.md` 打包至 core resources |
| **meta-claw-vessel** | 去除 `default-expert.yaml`，复用 `vessel.tmpl.md` 模板（原 expert.tmpl.md） |
| **Observability** | Micrometer Metrics（LLM 调用延迟、Token 消耗）；结构化日志（JSON 格式，SLF4J + Logback） |

**验收标准**：
- `meta-claw-session` 模块已删除，全部功能在 `meta-claw-core` 正常运行
- 对话历史持久化到 `vessels/{id}/conversations/{sessionKey}/history.jsonl`
- 用户偏好持久化到 `vessels/{id}/preferences/preferences.jsonl`
- Prompt 模板从 classpath 加载，支持 Vessel 级覆盖

### Phase 3：工具引擎 + 知识库 + 审批流

**目标**：支持本地工具调用、领域知识库、对话上下文管理、审批流。

| 模块 | 内容 |
|------|------|
| **meta-claw-tool** | `ToolRegistry`：方法反射扫描（`@Tool` 注解）、`JsonSchemaGenerator`（从 Java 类型生成 JSON Schema）、`ToolExecutor`（本地反射执行、超时控制、异常隔离）、并行执行（Semaphore 限流）、审批流（`approvalRequired` 配置） |
| **meta-claw-store/knowledge** | `KnowledgeManager`：Git-backed Markdown 存储、YAML frontmatter 解析（`KnowledgeEntry`）、关键词检索、`KnowledgeAnalyzer`（LLM 辅助分析）、矛盾检测（旧事实标记 SUPERSEDED）、分支实验（`branch/checkout/merge`） |
| **meta-claw-core** | `VesselRuntime` 集成：Prompt 组装注入 Knowledge + Preferences；LLM 返回 SpiToolCall 时路由到 `ToolExecutor`；工具结果回注消息历史并循环调用；上下文窗口截断策略（最近 N 轮 / Token 限制） |
| **meta-claw-cli** | 审批流交互：工具调用前暂停，终端渲染确认提示（Y/n），支持批量审批（并行工具时汇总所有待审批项）；`serve` / `start` / `stop` 后台 daemon |

**验收标准**：对话中 LLM 能调用本地工具（如文件读取、计算）；多轮对话记住上下文；Vessel 能基于知识库回答专业问题；危险工具执行前要求确认。

### Phase 4：MCP 客户端 + 技能系统

**目标**：支持外部 MCP Server 和动态技能加载。

| 模块 | 内容 |
|------|------|
| **meta-claw-mcp** | `McpClient`：stdio / SSE 传输、初始化（`initialize`）、工具发现（`tools/list`）、工具调用（`tools/call`）、将 MCP 工具适配为 `SpiToolDefinition` |
| **meta-claw-skill** | `SkillLoader`：文件系统监控（WatchService）热加载；`SkillRegistry`：注册/卸载；技能包格式（`skill.yaml` + 脚本/类文件），参考 expert_project `default_skills` 结构 |
| **meta-claw-core** | `CompositeToolRegistry`：统一聚合 local tools（meta-claw-tool）+ mcp tools（meta-claw-mcp）+ skill methods（meta-claw-skill），对外提供统一 `List<SpiToolDefinition>` |

**验收标准**：能连接外部 MCP Server（如 filesystem、fetch）；技能目录文件变动后自动热加载。

### Phase 5：百炼平台接入（未来）

**目标**：对接阿里云百炼平台，实现企业级提示词管理。

| 能力 | 说明 |
|------|------|
| **提示词版本管理** | 对接百炼 Prompt Hub，支持 prompt 版本化存储、回滚、对比 |
| **A/B 测试** | 同一 Vessel 可配置多个 prompt 版本，按流量比例分流，收集效果数据 |
| **效果评估** | 对接百炼评估接口，收集 prompt 效果指标（准确率、用户满意度、Token 消耗） |
| **在线调优** | 支持通过百炼平台在线编辑 prompt，实时生效或灰度发布 |
| **模板分离** | `system.tmpl.md` / `context.tmpl.md` 从 core resources 迁移至百炼平台管理，本地保留缓存和降级能力 |

---

## 7. 技术栈

| 层级 | 技术选型 |
|------|----------|
| 基础框架 | Spring Boot 3.2、Spring AI 1.1.4 |
| LLM 调用 | Spring AI ChatClient（包装为 `SpiLlmClient` SPI） |
| CLI | picocli + JLine |
| 序列化 | Jackson 2.18 + SnakeYAML 2.2 |
| 日志 | SLF4J 2.0 + Logback 1.5（JSON 结构化输出） |
| 观测性 | Micrometer + OpenTelemetry |
| 测试 | JUnit 5.10 + Mockito 5.x |
| 构建 | Maven 多模块 |

---

## 8. 风险与假设

| 风险 | 缓解措施 |
|------|----------|
| Spring AI 1.1.4 API 变动 | 通过 `SpiLlmClient` SPI 隔离，底层实现可替换 |
| MCP 协议版本不兼容 | 参考官方 `mcp-sdk-java`，协议层独立封装 |
| 知识库 Git 操作冲突 | 采用文件级锁或 Git 工作树隔离 |
| 后台 daemon 跨平台差异 | macOS 用 `launchd`，Linux 用 `systemd`，Windows 用服务包装器；优先支持 macOS/Linux |
| 多 Provider 配置切换 | `config.yaml` 中 `default_provider` + 各 provider 独立配置，Vessel 可覆盖 |
| JSONL 文件损坏或并发写入冲突 | 按 session/vessel 加 `ReentrantReadWriteLock`，定期备份 |
| Memory 与 Knowledge 概念混淆 | 代码和文档统一术语：Preferences = 用户偏好，Knowledge = 领域知识；存储路径分离 |
| 百炼平台 API 限流或不可用 | 本地模板作为降级方案，缓存百炼平台模板，支持离线运行 |

---

*文档版本：v2.1*  
*日期：2026-05-08*
