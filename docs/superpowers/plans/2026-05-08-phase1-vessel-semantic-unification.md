# Phase 1: Vessel 语义统一与基础功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 彻底消除 Expert 遗留概念，统一为 Vessel；完善模板加载与 CLI 基础命令，使 `meta-cli chat default` 能进行完整多轮对话。

**Architecture:** 将 `VesselConfig` 从 `meta-claw-vessel` 迁移至 `meta-claw-core` 作为通用 DTO；重命名 `ExpertManager→VesselManager`、`ExpertRuntime→VesselRuntime`、`ExpertResponseReady→VesselResponseReady`；扩展 `VesselConfigLoader` 解析 Markdown body；新增 CLI `init/create/list/delete/chat` 命令。

**Tech Stack:** Java 21, Maven, Spring Boot 3.2, picocli, Spring AI 1.1.4, Jackson, SnakeYAML

---

## File Structure

### Modified Files (Batch 1: Semantic Unification)

| File | Action | Reason |
|------|--------|--------|
| `meta-claw-core/src/main/java/meta/claw/core/model/VesselConfig.java` | Create | 从 `meta-claw-vessel` 迁移并扩展 |
| `meta-claw-core/src/main/java/meta/claw/core/model/ExpertConfig.java` | Delete | 被 `VesselConfig` 替代 |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselManager.java` | Create (rename from ExpertManager) | 语义统一 |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java` | Create (rename from ExpertRuntime) | 语义统一 |
| `meta-claw-core/src/main/java/meta/claw/core/events/VesselResponseReady.java` | Create (rename from ExpertResponseReady) | 语义统一 |
| `meta-claw-core/src/main/java/meta/claw/core/runtime/AgentLoop.java` | Modify | 更新引用 |
| `meta-claw-bootstrap/src/main/java/meta/claw/app/AppConfig.java` | Modify | 更新 Bean 定义和引用 |
| `meta-claw-bootstrap/src/main/java/meta/claw/app/MetaClawApplication.java` | Modify | 更新引用 |
| `meta-claw-gateway/src/main/java/meta/claw/gateway/Gateway.java` | Modify | 更新事件引用 |
| `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java` | Modify | 更新引用和描述 |
| `meta-claw-bootstrap/src/test/java/meta/claw/integration/MessageFlowIntegrationTest.java` | Modify | 更新引用和字符串 |
| `meta-claw-core/src/test/java/meta/claw/core/session/SessionManagerTest.java` | Modify | 更新字符串描述 |

### Modified Files (Batch 2: Template & Config Loading)

| File | Action | Reason |
|------|--------|--------|
| `meta-claw-core/src/main/java/meta/claw/core/model/VesselConfig.java` | Modify | 扩展字段（role, autoServe, excludeTools, identity, soul, domainKnowledge, capabilities, guidelines, preferences） |
| `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java` | Modify | 解析 Markdown body section |
| `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselTemplate.java` | Modify | 支持基于模板创建任意 Vessel |
| `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java` | Modify | 更新测试断言 |
| `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigResolverTest.java` | Modify | 更新引用（如有 Expert 字符串） |

### Created Files (Batch 2: CLI Commands)

| File | Action | Reason |
|------|--------|--------|
| `meta-claw-cli/src/main/java/meta/claw/cli/InitCommand.java` | Create | `meta-cli init` — 初始化目录结构 |
| `meta-claw-cli/src/main/java/meta/claw/cli/CreateCommand.java` | Create | `meta-cli create <name>` — 基于模板创建 Vessel |
| `meta-claw-cli/src/main/java/meta/claw/cli/ListCommand.java` | Create | `meta-cli list` — 列出所有 Vessel |
| `meta-claw-cli/src/main/java/meta/claw/cli/DeleteCommand.java` | Create | `meta-cli delete <name>` — 删除 Vessel |

### Modified Files (Batch 3: Chat Command Polish)

| File | Action | Reason |
|------|--------|--------|
| `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java` | Modify | 重构 REPL 交互、流式输出优化 |
| `meta-claw-cli/src/main/java/meta/claw/cli/MetaClawCommand.java` | Modify | 注册新命令（如有主命令入口） |

---

## Task 1: Migrate VesselConfig to meta-claw-core and delete ExpertConfig

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/model/VesselConfig.java`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/model/ExpertConfig.java`
- Modify: `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java` — 更新 import
- Modify: `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigResolver.java` — 更新 import
- Modify: `meta-claw-vessel/src/main/java/meta/claw/vessel/ResolvedVesselConfig.java` — 更新 import
- Modify: `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java` — 更新 import
- Modify: `meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigResolverTest.java` — 更新 import

- [ ] **Step 1: Create VesselConfig in meta-claw-core**

复制 `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfig.java` 到 `meta-claw-core/src/main/java/meta/claw/core/model/VesselConfig.java`，修改包名为 `meta.claw.core.model`。

- [ ] **Step 2: Delete ExpertConfig**

删除 `meta-claw-core/src/main/java/meta/claw/core/model/ExpertConfig.java`。

- [ ] **Step 3: Update imports in vessel module**

将所有 `import meta.claw.vessel.VesselConfig` 在 vessel 模块内改为 `import meta.claw.core.model.VesselConfig`。

涉及文件：
- `VesselConfigLoader.java`
- `VesselConfigResolver.java`
- `ResolvedVesselConfig.java`
- `VesselConfigLoaderTest.java`
- `VesselConfigResolverTest.java`

- [ ] **Step 4: Verify compile of vessel module**

---

## Task 2: Rename ExpertManager → VesselManager

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselManager.java`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/runtime/ExpertManager.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/AgentLoop.java`
- Modify: `meta-claw-bootstrap/src/main/java/meta/claw/app/AppConfig.java`
- Modify: `meta-claw-bootstrap/src/main/java/meta/claw/app/MetaClawApplication.java`
- Modify: `meta-claw-bootstrap/src/test/java/meta/claw/integration/MessageFlowIntegrationTest.java`

- [ ] **Step 1: Create VesselManager**

复制 `ExpertManager.java` 内容到 `VesselManager.java`，修改：
- 包名不变
- 类名 `ExpertManager` → `VesselManager`
- 字段/变量名 `expert` → `vessel`（保持驼峰：`experts` → `vessels`, `expertId` → `vesselId` 等）
- 方法名 `getConfig`/`getRuntime`/`registerRuntime`/`listAvailableExperts`/`hasExpert` → `getConfig`/`getRuntime`/`registerRuntime`/`listAvailableVessels`/`hasVessel`
- 注释中的 "Expert" → "Vessel"
- `DEFAULT_EXPERTS_DIR` → `DEFAULT_VESSELS_DIR`
- `EXPERT_CONFIG_FILE` → `VESSEL_CONFIG_FILE`
- `ExpertConfig` → `VesselConfig`
- `ExpertRuntime` → `VesselRuntime`

- [ ] **Step 2: Delete ExpertManager.java**

- [ ] **Step 3: Update AgentLoop.java**

替换所有 `ExpertManager` → `VesselManager`，`expertManager` → `vesselManager`。
替换 `ExpertConfig` → `VesselConfig`。
替换注释中的 "Expert" → "Vessel"。
替换 `ExpertRuntime` → `VesselRuntime`。
替换 `ExpertResponseReady` → `VesselResponseReady`。
方法 `determineTargetExpert` → `determineTargetVessel`。

- [ ] **Step 4: Update AppConfig.java**

替换所有 `ExpertManager` → `VesselManager`。
替换所有 `ExpertConfig` → `VesselConfig`。
替换所有 `ExpertRuntime` → `VesselRuntime`。
替换 Bean 方法名 `expertManager()` → `vesselManager()`。
替换 `expertsDir` → `vesselsDir`。
替换注释中的 "Expert" → "Vessel"。

- [ ] **Step 5: Update MetaClawApplication.java**

替换 `ExpertManager` → `VesselManager`。
替换 `expertManager` → `vesselManager`。
替换注释中的 "Expert" → "Vessel"。

- [ ] **Step 6: Update MessageFlowIntegrationTest.java**

替换所有 `ExpertManager` → `VesselManager`。
替换所有 `ExpertConfig` → `VesselConfig`。
替换所有 `ExpertRuntime` → `VesselRuntime`。
替换所有 `ExpertResponseReady` → `VesselResponseReady`。
替换所有 "Expert" 字符串 → "Vessel"。

---

## Task 3: Rename ExpertRuntime → VesselRuntime

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/runtime/ExpertRuntime.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/AgentLoop.java`（已在上一步处理）
- Modify: `meta-claw-bootstrap/src/main/java/meta/claw/app/AppConfig.java`（已在上一步处理）

- [ ] **Step 1: Create VesselRuntime.java**

复制 `ExpertRuntime.java` 内容，修改：
- 类名 `ExpertRuntime` → `VesselRuntime`
- `ExpertConfig` → `VesselConfig`
- 变量名/注释中的 `expert` → `vessel`
- `getExpertId()` → `getVesselId()`

- [ ] **Step 2: Delete ExpertRuntime.java**

---

## Task 4: Rename ExpertResponseReady → VesselResponseReady

**Files:**
- Create: `meta-claw-core/src/main/java/meta/claw/core/events/VesselResponseReady.java`
- Delete: `meta-claw-core/src/main/java/meta/claw/core/events/ExpertResponseReady.java`
- Modify: `meta-claw-core/src/main/java/meta/claw/core/runtime/AgentLoop.java`
- Modify: `meta-claw-gateway/src/main/java/meta/claw/gateway/Gateway.java`
- Modify: `meta-claw-bootstrap/src/test/java/meta/claw/integration/MessageFlowIntegrationTest.java`

- [ ] **Step 1: Create VesselResponseReady.java**

复制 `ExpertResponseReady.java`，修改类名和注释。

- [ ] **Step 2: Delete ExpertResponseReady.java**

- [ ] **Step 3: Update Gateway.java**

替换所有 `ExpertResponseReady` → `VesselResponseReady`。
替换注释中的 "Expert" → "Vessel"。

---

## Task 5: Update ChatCommand and remaining references

**Files:**
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`
- Modify: `meta-claw-core/src/test/java/meta/claw/core/session/SessionManagerTest.java`

- [ ] **Step 1: Update ChatCommand.java**

替换 `@Command` description 中的 "expert" → "vessel"。
替换 `expertName` → `vesselName`。
替换变量/注释中的 "expert" → "vessel"。

- [ ] **Step 2: Update SessionManagerTest.java**

替换注释中的 "Expert" → "Vessel"。

---

## Task 6: Extend VesselConfig and VesselConfigLoader

**Files:**
- Modify: `meta-claw-core/src/main/java/meta/claw/core/model/VesselConfig.java`
- Modify: `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java`

- [ ] **Step 1: Add fields to VesselConfig**

新增字段：`role` (String), `autoServe` (boolean), `excludeTools` (List<String>), `identity` (String), `soul` (String), `domainKnowledge` (String), `capabilities` (String), `guidelines` (String), `preferences` (String)。

- [ ] **Step 2: Parse Markdown body in VesselConfigLoader**

在 `extractYamlFrontmatter` 之后，新增 `extractMarkdownSections` 方法，按 `## ` 分割 Markdown body，提取各 section 内容并存入 VesselConfig。

---

## Task 7: Enhance VesselTemplate

**Files:**
- Modify: `meta-claw-vessel/src/main/java/meta/claw/vessel/VesselTemplate.java`
- Modify: `meta-claw-vessel/src/main/resources/templates/vessel.tmpl.md`

- [ ] **Step 1: Load external template**

修改 `VesselTemplate` 从 classpath 加载 `templates/vessel.tmpl.md`，而非使用硬编码字符串。

- [ ] **Step 2: Add createVessel method**

新增 `createVessel(Path vesselsDir, String name, String description)` 方法，渲染模板占位符（`{name}`、`{created_at}`、`{description}`）并创建完整目录结构（skills/, knowledge/, conversations/, preferences/）。

---

## Task 8: Create CLI Commands

**Files:**
- Create: `meta-claw-cli/src/main/java/meta/claw/cli/InitCommand.java`
- Create: `meta-claw-cli/src/main/java/meta/claw/cli/CreateCommand.java`
- Create: `meta-claw-cli/src/main/java/meta/claw/cli/ListCommand.java`
- Create: `meta-claw-cli/src/main/java/meta/claw/cli/DeleteCommand.java`
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/MetaClawCommand.java`（或主入口类）

- [ ] **Step 1: InitCommand**

实现 `meta-cli init`：创建 `~/.meta-claw/config.yaml`（如不存在）、`vessels/default/`、`skills/`。调用 `VesselTemplate.createDefaultVessel()`。

- [ ] **Step 2: CreateCommand**

实现 `meta-cli create <name>`：调用 `VesselTemplate.createVessel()`。

- [ ] **Step 3: ListCommand**

实现 `meta-cli list`：扫描 `~/.meta-claw/vessels/` 目录，列出所有 Vessel（读取 vessel.md 的 name 和 description）。

- [ ] **Step 4: DeleteCommand**

实现 `meta-cli delete <name>`：删除 `vessels/{name}/` 目录，带确认提示。

- [ ] **Step 5: Register commands**

在主命令入口中注册上述命令。

---

## Task 9: Polish ChatCommand

**Files:**
- Modify: `meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java`

- [ ] **Step 1: Refactor to use VesselManager**

如果可能，让 ChatCommand 通过 `VesselManager` 获取 Vessel 配置和运行时，而非直接解析文件。

- [ ] **Step 2: Integrate JsonlConversationStore**

在对话结束后，将用户消息和 AI 回复追加到 `JsonlConversationStore`。

- [ ] **Step 3: Verify end-to-end flow**

运行 `meta-cli chat default`，验证多轮对话正常。

---

## Self-Review Checklist

- [ ] Spec coverage: Batch 1-3 全部任务已对应到 roadmap 文档
- [ ] Placeholder scan: 无 TBD/TODO
- [ ] Type consistency: `VesselConfig` 在 core 中定义，vessel 模块通过 import 引用
