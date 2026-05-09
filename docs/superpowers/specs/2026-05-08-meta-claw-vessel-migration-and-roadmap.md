# Meta-Claw Vessel 语义统一与分批实施路线图

> 日期：2026-05-08（修订版 2026-05-09）
> 目标：彻底消除 Expert 遗留概念，统一为 Vessel（数字员工）；完善模板加载与 CLI 命令体系；吸收参考项目优点，规划长期演进路径。

---

## 一、现状盘点：Expert 遗留问题清单

当前代码中仍有大量 `Expert` 命名，与 v2 设计文档的 `Vessel` 概念并存，造成语义混乱。必须全部替换。

### 1.1 必须替换的类/接口

| 当前名称 | 目标名称 | 所在模块 | 影响范围 | 状态 |
|---------|---------|---------|---------|------|
| `ExpertConfig` | 删除，复用 `meta-claw-core.VesselConfig` | meta-claw-core | 被 ExpertManager、ExpertRuntime、AppConfig 引用 | ✅ 已完成 |
| `ExpertManager` | `VesselManager` | meta-claw-core | 被 AgentLoop、AppConfig 引用 | ✅ 已完成 |
| `ExpertRuntime` | `VesselRuntime` | meta-claw-core | 被 AgentLoop、AppConfig 引用 | ✅ 已完成 |
| `ExpertResponseReady` | `VesselResponseReady` | meta-claw-core | 被 Gateway、AgentLoop 引用 | ✅ 已完成 |
| `SessionManagerTest` 中的 "Expert" 字符串 | 改为 "Vessel" | meta-claw-core test | 测试用例描述 | ⚠️ 部分完成，注释残留 |

### 1.2 必须替换的方法/变量/注释

| 文件 | 替换内容 | 状态 |
|-----|---------|------|
| `ExpertManager.java` → `VesselManager.java` | 类名、构造参数 `expertsDir` → `vesselsDir`、`DEFAULT_EXPERTS_DIR` → `DEFAULT_VESSELS_DIR`、所有方法名中的 `expert` → `vessel`、`Expert` → `Vessel` | ✅ 已完成 |
| `ExpertRuntime.java` → `VesselRuntime.java` | 类名、构造参数中的 `ExpertConfig` → `VesselConfig` | ✅ 已完成 |
| `AgentLoop.java` | 引用 `ExpertManager` → `VesselManager`、`ExpertRuntime` → `VesselRuntime` | ✅ 已完成 |
| `AppConfig.java` | `expertManager()` → `vesselManager()`、`initializeRuntimes()` 中的 `ExpertConfig` → `VesselConfig`、`ExpertRuntime` → `VesselRuntime`、`ExpertManager` → `VesselManager` | ⚠️ 代码已完成，注释中仍有"专家"残留 |
| `Gateway.java` | `ExpertResponseReady` → `VesselResponseReady` | ✅ 已完成 |
| `ChatCommand.java` | `ExpertConfig` → `VesselConfig`、`ExpertRuntime` → `VesselRuntime` | ⚠️ 代码已完成，注释引用 `expert_cli/cli.py` 未更新 |
| `MessageFlowIntegrationTest.java` | `ExpertResponseReady` → `VesselResponseReady` | ✅ 已完成 |
| `MetaClawApplication.java` | 如有 expert 引用则替换 | ✅ 已完成 |
| `UserSession.java` / `SessionManager.java` / `ChatMode.java` / `ChatMessage.java` | 注释中的"专家"→"数字员工/Vessel" | ⚠️ 待清理 |

### 1.3 配置与资源文件

| 文件 | 处理方案 | 状态 |
|-----|---------|------|
| `meta-claw-bootstrap/src/main/resources/experts/` | 目录重命名为 `vessels/`，配置格式从 `expert.yaml` 迁移为 `vessel.md` | ✅ 已完成 |
| `AppConfig.java` 中的 `${meta.claw.experts.dir}` | 改为 `${meta.claw.vessels.dir}`，默认值改为 `vessels` | ✅ 已完成 |

---

## 二、模板加载逻辑修复（Phase 1 核心）

### 2.1 当前问题

- `VesselConfigLoader` 已能解析 `config.yaml` 的 YAML frontmatter 和 `vessel.md` 的 Markdown body（Identity、Soul、Domain Knowledge 等 section）
- `VesselTemplate` 已使用 `vessel-config.tmpl.yaml` + `vessel.tmpl.md` 作为创建新 Vessel 的模板
- `VesselConfig` 已包含 `role`、`autoServe`、`excludeTools` 等字段

### 2.2 修复方案（已实现）

#### A. 扩展 VesselConfig 模型

```java
public class VesselConfig {
    // YAML frontmatter 字段
    private String id;
    private String name;
    private String description;
    private String emoji;
    private String model;
    private String systemPrompt;
    private boolean preferencesEnabled;
    private String knowledgeDir;
    
    // 新增字段（来自 vessel-config.tmpl.yaml）
    private String role;           // teamleader / member
    private boolean autoServe;     // 无参数 serve 时是否自动启动
    private List<String> excludeTools;
    
    // 解析后的 Markdown body section
    private String identity;       // ## Identity 内容
    private String soul;           // ## Soul 内容
    private String domainKnowledge; // ## Domain Knowledge 内容
    private String capabilities;   // ## Capabilities 内容
    private String guidelines;     // ## Guidelines 内容
    private String preferences;    // ## Preferences 内容
}
```

✅ 已实现于 `meta-claw-core/src/main/java/meta/claw/core/model/VesselConfig.java`

#### B. 扩展 VesselConfigLoader

- 解析 `config.yaml` 的 YAML frontmatter
- 解析 `vessel.md` 的 Markdown body，按 `## SectionName` 分割提取各 section 内容

✅ 已实现于 `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java`

⚠️ **已知问题**：`VesselConfigLoader.mapToConfig()` 使用下划线命名字段（`system_prompt`、`preferences_enabled`、`exclude_tools`、`auto_serve`），而 `VesselManager.mapToVesselConfig()` 使用驼峰命名（`systemPrompt`、`preferencesEnabled`、`knowledgeDir`、`excludeTools`）。两者字段映射风格不统一，需要在后续批次中统一为同一种风格（建议统一为下划线，与模板文件保持一致）。

#### C. VesselTemplate（参考 expert_cli/cli.py 的 create 命令）

```java
public class VesselTemplate {
    /**
     * 基于模板创建新 Vessel 目录结构
     */
    public void createVessel(Path vesselsDir, String name, String description);
    
    /**
     * 渲染模板占位符：{name}、{created_at}、{description}
     */
    private String renderTemplate(String template, Map<String, String> vars);
}
```

创建目录结构：
```
vessels/{name}/
├── config.yaml        # 从 vessel-config.tmpl.yaml 渲染（YAML frontmatter）
├── vessel.md          # 从 vessel.tmpl.md 渲染（Markdown body）
├── skills/
├── knowledge/
├── conversations/     # 运行时自动生成
└── preferences/       # 运行时自动生成
```

✅ 已实现于 `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselTemplate.java`

⚠️ **已知问题**：`VesselManager.loadVessels()` 仍试图从 `vessel.md` 用 SnakeYAML 解析 YAML frontmatter，但实际 frontmatter 已迁移到 `config.yaml`。这导致 Bootstrap 启动时 `VesselManager` 无法正确加载 Vessel 配置。`VesselManager` 需要重构为使用 `VesselConfigLoader`。

---

## 三、CLI 命令体系设计

参考 `expert_cli/cli.py`，规划 `meta-claw-cli` 的命令体系：

### 3.1 Phase 1 必须跑通的命令

| 命令 | 功能 | 优先级 | 状态 |
|-----|------|--------|------|
| `meta-cli init` | 初始化 `~/.meta-claw/`：config.yaml + vessels/default/ + skills/ | P0 | ✅ 已完成 |
| `meta-cli create <name>` | 基于 vessel.tmpl.md 创建新 Vessel | P0 | ✅ 已完成 |
| `meta-cli chat <name>` | 本地交互式对话（单消息 / REPL 模式） | P0 | ✅ 已完成 |
| `meta-cli list` | 列出所有 Vessel | P0 | ✅ 已完成 |
| `meta-cli delete <name>` | 删除 Vessel | P1 | ✅ 已完成 |
| `meta-cli config get/set/list` | 操作 `~/.meta-claw/config.yaml` | P1 | ✅ 已完成 |

### 3.2 Phase 2-3 命令规划

| 命令 | 功能 | 优先级 | 状态 |
|-----|------|--------|------|
| `meta-cli serve [names...]` | 前台启动 WebSocket 服务 | P1 | ⏳ 待实现 |
| `meta-cli start [names...]` | 后台 daemon 启动（alias: serve --daemon） | P1 | ⏳ 待实现 |
| `meta-cli stop` | 停止后台服务 | P1 | ⏳ 待实现 |
| `meta-cli restart [names...]` | 重启后台服务 | P1 | ⏳ 待实现 |
| `meta-cli status` | 查看运行状态 | P2 | ⏳ 待实现 |
| `meta-cli logs` | 查看日志 | P2 | ⏳ 待实现 |

### 3.3 chat 命令交互设计（参考 expert_cli/cli.py）

```
$ meta-cli chat default

🤖 Default Vessel
A general-purpose AI assistant
─────────────────────────────
[Ctrl+D] Quit | /exit | /clear | ↑↓ History

You > 查一下北京天气

● Response...
北京今天晴朗，气温 18-28°C...

You > 
```

内联命令：
- `/exit` — 退出
- `/clear` — 清空当前对话历史
- `/tools` — 列出可用工具
- `/skills` — 列出可用技能
- `/memory` — 查看当前记忆

✅ 基础 REPL + 流式输出 + `/exit` + `/clear` 已实现
⚠️ `/tools`、`/skills`、`/memory` 待实现
⚠️ 对话历史未持久化到 `JsonlConversationStore`（当前仅存于内存 ArrayList）

---

## 四、参考项目优点吸收

### 4.1 来自 expert-cli-tech-reference.md

| 优点 | 吸收方案 | 优先级 | 状态 |
|-----|---------|--------|------|
| 多 Expert 隔离架构 | Vessel 目录完全隔离（已有），确保 conversations/ 和 preferences/ 也隔离 | P0 | ✅ 已实现 |
| 工具自动发现 | Phase 3 实现 `ToolRegistry` 自动扫描 `@Tool` 注解 | P2 | ⏳ 待实现 |
| Skill 即提示词 | Phase 4 实现 Skill 为 Markdown 文件，注入系统提示 | P3 | ⏳ 待实现 |
| EventBus 解耦（已有） | 保持现有设计，扩展更多事件类型 | P1 | ✅ 已有基础 |
| 分层日志 | 按模块拆分日志（cli/gateway/vessel/agent_loop/tools） | P2 | ⏳ 待实现 |
| 多 Provider 统一接入（已有） | 保持 `SpiLlmClient` SPI 设计 | — | ✅ 已实现 |
| MCP 协议集成 | Phase 4 实现 `meta-claw-mcp` | P3 | ⏳ 待实现 |
| Cron 定时任务 | Phase 5 考虑添加定时任务支持 | P5 | ⏳ 待实现 |

### 4.2 来自 CowAgent

| 优点 | 吸收方案 | 优先级 | 状态 |
|-----|---------|--------|------|
| 多渠道并发架构 | `ChannelManager` 用独立线程管理多通道，支持热插拔 | P2 | ⏳ 待实现 |
| Bridge 桥接层 | 新增 `Bridge` 单例，统一调度 chat/语音/翻译 | P3 | ⏳ 待实现 |
| 插件事件系统 | 四阶段事件（ON_RECEIVE / ON_HANDLE / ON_DECORATE / ON_SEND）+ 优先级 | P3 | ⏳ 待实现 |
| 会话隔离（已有改进） | 每个 session 独立队列 + 信号量，避免消息串扰 | P1 | ✅ 已有基础 |
| 工具阶段 | `ToolStage`：PRE_PROCESS（Agent 选择）/ POST_PROCESS（答案后自动执行） | P3 | ⏳ 待实现 |
| 上下文智能压缩 | Token 超出时自动压缩历史，保留核心信息 | P2 | ⏳ 待实现 |
| 缺依赖优雅降级 | 工具加载失败时记录警告而非崩溃 | P2 | ⏳ 待实现 |
| 条件工具加载 | 依赖 API Key 的工具，缺配置时静默禁用 | P2 | ⏳ 待实现 |

### 4.3 来自 Claude Code

| 优点 | 吸收方案 | 优先级 | 状态 |
|-----|---------|--------|------|
| 权限系统 | `PermissionMode` + 规则引擎（alwaysAllow/alwaysDeny/alwaysAsk） | P3 | ⏳ 待实现 |
| 查询引擎 QueryEngine | `QueryEngine` 管理消息历史、Token 限制、费用追踪、后台任务 | P2 | ⏳ 待实现 |
| 工具 UI 渲染 | 工具调用时显示进度/结果（终端 UI） | P3 | ⏳ 待实现 |
| MCP 完整客户端（已有规划） | stdio/sse/http/ws 多传输、OAuth、资源读取 | P3 | ⏳ 待实现 |
| 状态管理 | 自定义轻量 Store（消息历史、权限上下文、工具状态） | P2 | ⏳ 待实现 |
| 对话压缩 compact | Token 超限时自动压缩对话历史 | P2 | ⏳ 待实现 |
| Bash 安全解析链 | AST → 命令分类 → 路径约束 → 权限规则 | P4 | ⏳ 待实现 |

---

## 五、分批实施路径

### Batch 1：语义统一 ⚠️ 部分完成

**目标**：彻底消除 Expert 遗留，全部替换为 Vessel。

- [x] `ExpertConfig` → 删除，统一使用 `meta-claw-core.VesselConfig`（从 vessel 模块迁移至 core）
- [x] `ExpertManager` → `VesselManager`
- [x] `ExpertRuntime` → `VesselRuntime`
- [x] `ExpertResponseReady` → `VesselResponseReady`
- [x] `AppConfig` 中所有 expert 引用 → vessel
- [x] `AgentLoop` 中所有 expert 引用 → vessel
- [x] `Gateway` 中 `ExpertResponseReady` → `VesselResponseReady`
- [x] `ChatCommand` 中所有 expert 引用 → vessel
- [x] 测试文件中的 expert 字符串 → vessel
- [x] 配置项 `meta.claw.experts.dir` → `meta.claw.vessels.dir`
- [x] `UserSession.targetExpert` → `targetVessel`
- [x] `SessionManager` 参数/方法名中的 expert → vessel
- [x] `ChatMessage.expertName` → `vesselName`
- [ ] 清理所有 Java 注释中的 "Expert" / "专家" → "Vessel" / "数字员工"（`SessionManager`、`UserSession`、`ChatMode`、`ChatMessage`、`AppConfig`、`ChatCommand` 等仍有残留）

### Batch 2：模板与配置加载 ✅ 已完成

**目标**：vessel.tmpl.md 正确使用，VesselConfigLoader 完整解析 vessel.md。

- [x] 扩展 `VesselConfig`：新增 `role`、`autoServe`、`excludeTools`、`identity`、`soul`、`domainKnowledge`、`capabilities`、`guidelines`、`preferences`
- [x] 扩展 `VesselConfigLoader`：解析 Markdown body 的各 section（Identity、Soul、Domain Knowledge、Capabilities、Guidelines、Preferences）
- [x] 增强 `VesselTemplate`：从 classpath 加载 `templates/vessel-config.tmpl.yaml` + `templates/vessel.tmpl.md`，支持基于模板创建任意 Vessel
- [x] `meta-claw-cli init`：创建 `~/.meta-claw/config.yaml` + `vessels/default/` + `skills/`
- [x] `meta-claw-cli create <name>`：调用 `VesselTemplate.createVessel()`
- [x] `meta-claw-cli list`：扫描 `vessels/` 目录列出所有 Vessel
- [x] `meta-claw-cli delete <name>`：删除 Vessel 目录（带确认提示）
- [x] `meta-claw-cli config get/set/list`：操作全局配置

⚠️ **遗留问题**：`VesselManager.loadVessels()` 与 `VesselConfigLoader` 的实现路径不一致，且字段命名风格不同。`VesselManager` 需要重构以复用 `VesselConfigLoader`。

### Batch 3：chat 命令跑通 ⚠️ 部分完成

**目标**：`meta-cli chat default` 能进行完整的多轮对话。

- [x] `meta-claw-cli chat <name>`：REPL 交互模式（已有实现）
- [x] 流式输出到终端（Spring AI ChatClient 流式回调）
- [x] 内联命令支持：`/exit`、`/clear`
- [x] `VesselConfigResolver` 正确解析 provider、model、temperature
- [x] `meta-claw-store` 模块：创建 `JsonlConversationStore` + `FilePreferenceStore`
- [ ] Prompt 模板加载：`SystemPromptBuilder` + `TemplateLoader` 实际运行（Batch 5 完善）
- [ ] 对话历史追加到 `JsonlConversationStore`（当前 ChatCommand 仍使用内存 ArrayList，未集成持久化）

### Batch 4：存储层完善（Phase 2）

- [ ] `JsonlConversationStore` 完整测试（并发写入、media 存储、历史读取）
- [ ] `FilePreferenceStore` 完整测试（add/lookup/listRecent/delete）
- [ ] `MemoryManager`：上下文窗口截断策略（最近 N 轮 / Token 限制）
- [ ] 异步持久化：对话历史和用户偏好的异步写入
- [ ] ChatCommand 集成 `JsonlConversationStore`：启动时加载历史、每次对话后追加

### Batch 5：Prompt Engineering 与知识库（Phase 2-3）

- [ ] `SystemPromptBuilder` 完整实现：加载 system.tmpl.md + context.tmpl.md + vessel.md
- [ ] `PromptContext` 组装：identity + tools + skills + knowledge + preferences + workspace + runtime
- [ ] `meta-claw-store/knowledge`：Git-backed Markdown 领域知识存储
- [ ] `KnowledgeRetriever`：关键词检索 + YAML frontmatter 解析

### Batch 6：工具引擎（Phase 3）

- [ ] `ToolRegistry`：`@Tool` 注解扫描、JSON Schema 生成
- [ ] `ToolExecutor`：本地反射执行、超时控制、异常隔离
- [ ] 审批流：`approvalRequired` 配置 + 终端确认交互
- [ ] `CompositeToolRegistry`：聚合本地工具 + MCP 工具 + 技能方法

### Batch 7：MCP + 技能系统（Phase 4）

- [ ] `meta-claw-mcp`：MCP 客户端、工具发现、调用
- [ ] `meta-claw-skill`：文件系统热加载（WatchService）、动态注册卸载
- [ ] `SkillLoader`：加载 `SKILL.md` 并注入系统提示

### Batch 8：高级功能（Phase 5+）

- [ ] 百炼平台接入：提示词版本管理、A/B 测试
- [ ] 权限系统：`PermissionMode` + 规则引擎
- [ ] 查询引擎 `QueryEngine`：消息管理、Token 限制、费用追踪
- [ ] 后台任务与定时任务
- [ ] 多渠道并发 `ChannelManager`
- [ ] Web 控制台

---

## 六、关键文件变更汇总

### 6.1 新增文件

```
meta-claw-core/src/main/java/meta/claw/core/prompt/
├── PromptContext.java               ⏳ Batch 5
├── SystemPromptBuilder.java         ⏳ Batch 5
├── TemplateLoader.java              ⏳ Batch 5

meta-claw-vessel/src/main/java/meta/claw/vessel/
├── VesselTemplate.java              ✅ 已实现

meta-claw-cli/src/main/java/meta/claw/cli/
├── InitCommand.java                 ✅ 已实现
├── CreateCommand.java               ✅ 已实现
├── ListCommand.java                 ✅ 已实现
├── DeleteCommand.java               ✅ 已实现
├── ConfigCommand.java               ✅ 已实现
├── ChatCommand.java（重构）         ✅ 已实现
├── ServeCommand.java                ⏳ Batch 3+
├── StartCommand.java                ⏳ Batch 3+
├── StopCommand.java                 ⏳ Batch 3+

meta-claw-store/src/main/java/meta/claw/store/conversation/
├── JsonlConversationStore.java      ✅ 已实现

meta-claw-store/src/main/java/meta/claw/store/preferences/
├── FilePreferenceStore.java         ✅ 已实现
```

### 6.2 修改文件

```
meta-claw-core/src/main/java/meta/claw/core/model/
├── ExpertConfig.java          → 删除，复用 meta-claw-vessel.VesselConfig ✅

meta-claw-core/src/main/java/meta/claw/core/runtime/
├── ExpertManager.java         → VesselManager.java                     ✅
├── ExpertRuntime.java         → VesselRuntime.java                     ✅
├── AgentLoop.java             → 更新引用                              ✅

meta-claw-core/src/main/java/meta/claw/core/events/
├── ExpertResponseReady.java   → VesselResponseReady.java               ✅

meta-claw-vessel/src/main/java/meta/claw/vessel/
├── VesselConfig.java          → 扩展字段                              ✅
├── VesselConfigLoader.java    → 扩展 Markdown body 解析                ✅

meta-claw-bootstrap/src/main/java/meta/claw/app/
├── AppConfig.java             → 更新所有 Expert 引用                  ⚠️ 注释待清理

meta-claw-gateway/src/main/java/meta/claw/gateway/
├── Gateway.java               → 更新事件引用                          ✅
```

### 6.3 删除文件

```
meta-claw-core/src/main/java/meta/claw/core/model/ExpertConfig.java       ✅
meta-claw-bootstrap/src/main/resources/experts/（目录重命名）              ✅
```

---

## 七、架构已知问题与风险

### 7.1 VesselManager 与 VesselConfigLoader 配置加载路径不一致（🔴 高优先级）

**问题描述**：
- `VesselConfigLoader` 从 `config.yaml` 读取 frontmatter，从 `vessel.md` 读取 Markdown body
- `VesselManager.loadVessels()` 仍直接从 `vessel.md` 用 SnakeYAML 解析 frontmatter
- 实际运行时的 `vessel.md` 已不含 YAML frontmatter（frontmatter 已迁移到 `config.yaml`）
- 这导致 Bootstrap 启动时 `VesselManager` 无法正确加载任何 Vessel 配置

**影响**：AgentLoop、Gateway 等依赖 VesselManager 的组件在 Bootstrap 模式下无法工作。

**修复方案**：重构 `VesselManager.loadVessels()`，使其调用 `VesselConfigLoader.loadFromDirectory()` 统一加载逻辑。

### 7.2 字段命名风格不统一（🟡 中优先级）

**问题描述**：
- `VesselConfigLoader` 使用下划线命名（`system_prompt`、`preferences_enabled`）
- `VesselManager` 使用驼峰命名（`systemPrompt`、`preferencesEnabled`）
- 模板文件 `vessel-config.tmpl.yaml` 使用下划线命名

**修复方案**：统一 `VesselManager` 的字段映射为下划线命名，与模板文件和 `VesselConfigLoader` 保持一致。

### 7.3 config.yaml 模板拼写错误（🟡 中优先级）

**问题描述**：`vessel-config.tmpl.yaml` 第 21 行 `provide:` 应为 `provider:`。

**修复方案**：修正模板文件中的拼写错误。

---

## 八、验收标准

### Batch 1 验收
- [ ] 全项目 grep "Expert"（除注释外）结果为空
- [ ] 全项目注释中 "专家" 全部替换为 "数字员工" / "Vessel"
- [ ] `mvn clean compile` 通过
- [ ] 所有测试通过

### Batch 2-3 验收（Phase 1 完整）
- [ ] `meta-cli init` 创建完整目录结构
- [ ] `meta-cli create my-vessel` 基于模板创建 Vessel
- [ ] `meta-cli chat default` 能进行多轮流式对话
- [ ] `meta-cli list` 显示所有 Vessel
- [ ] 对话历史写入 `vessels/default/conversations/{sessionKey}/history.jsonl`

---

*文档版本：v1.1（修订版）*  
*修订日期：2026-05-09*  
*修订说明：补充 meta-claw-store 模块状态、修正 Batch 完成度、添加已知问题章节*
