# Meta-Claw 实施进度报告与下一步计划

> 日期：2026-05-09
> 范围：基于代码审计对比 `2026-05-08-meta-claw-vessel-migration-and-roadmap.md` 与实际代码差异，修正文档滞后，明确后续优先级

---

## 一、审计摘要

本次审计对比了设计文档与 `/Users/kai/IdeaProjects/meta_claw` 实际代码状态，发现以下关键差异：

1. **文档完全遗漏了已实现的 `meta-claw-store` 模块**
2. **多个 Batch 的完成度被高估**（注释残留、ChatCommand 未集成持久化）
3. **`VesselManager` 与 `VesselConfigLoader` 存在严重的架构不一致**，可能导致 Bootstrap 模式无法加载 Vessel
4. **字段命名风格在 `VesselManager` 与 `VesselConfigLoader` 之间不统一**
5. **模板文件存在拼写错误**（`provide:` → `provider:`）

以上问题已在 `2026-05-08-meta-claw-vessel-migration-and-roadmap.md` v1.1 修订版中修正。

---

## 二、各 Batch 实际完成度

| Batch | 目标 | 完成度 | 关键遗留 |
|-------|------|--------|----------|
| Batch 1 | 语义统一（Expert → Vessel） | ~90% | Java 注释中仍有 "专家" / "Expert" 残留 |
| Batch 2 | 模板与配置加载 | ~95% | VesselManager 未复用 VesselConfigLoader |
| Batch 3 | chat 命令跑通 | ~80% | 对话历史未持久化到 JsonlConversationStore |
| Batch 4 | 存储层完善 | ~40% | Store 实现已有，缺测试、缺集成、缺 MemoryManager |
| Batch 5 | Prompt Engineering | 0% | SystemPromptBuilder、TemplateLoader、PromptContext 均不存在 |
| Batch 6 | 工具引擎 | 0% | ToolRegistry、ToolExecutor 均不存在 |
| Batch 7 | MCP + 技能系统 | 0% | meta-claw-mcp、meta-claw-skill 模块均不存在 |
| Batch 8 | 高级功能 | 0% | — |

---

## 三、当前已实现的核心能力

### 3.1 模块结构

```
meta-claw/
├── meta-claw-core          ✅ SPI、EventBus、Session、模型、LlmClient
├── meta-claw-gateway       ✅ 网关、Channel 抽象、事件路由
├── meta-claw-gateway-weixin ✅ 微信渠道（openilink SDK）
├── meta-claw-bootstrap     ✅ Spring Boot 启动、AppConfig
├── meta-claw-vessel        ✅ VesselConfig、VesselConfigLoader、VesselTemplate、VesselConfigResolver
├── meta-claw-store         ✅ JsonlConversationStore、FilePreferenceStore
├── meta-claw-cli           ✅ init、create、list、delete、config、chat 命令
└── third-party/openilink-sdk-java ✅ 微信协议 SDK
```

### 3.2 已验证的端到端流程

| 流程 | 状态 | 验证方式 |
|------|------|----------|
| `meta-cli init` → 创建目录结构 | ✅ | 代码审查 + 手动测试 |
| `meta-cli create <name>` → 基于模板创建 Vessel | ✅ | 代码审查 + 手动测试 |
| `meta-cli chat default` → REPL 多轮流式对话 | ✅ | 代码审查 + 集成测试通过 |
| `meta-cli list` → 扫描 vessels 目录 | ✅ | 代码审查 |
| `meta-cli config set/get` → 操作全局配置 | ✅ | 单元测试通过 |
| `VesselConfigResolver` → 合并全局配置 + Vessel 配置 + profile | ✅ | 单元测试通过 |
| `SpringAiLlmClient` → 流式输出 | ✅ | 集成测试通过 |

### 3.3 已交付的测试覆盖

| 模块 | 测试文件 | 状态 |
|------|----------|------|
| meta-claw-core | `SessionManagerTest` | ✅ 存在 |
| meta-claw-core | `SpringAiLlmClientTest` | ✅ 存在 |
| meta-claw-core | `SpringAiLlmClientIntegrationTest` | ✅ 存在 |
| meta-claw-core | `MessageTest` | ✅ 存在 |
| meta-claw-vessel | `VesselConfigLoaderTest` | ✅ 存在 |
| meta-claw-vessel | `VesselConfigResolverTest` | ✅ 存在 |
| meta-claw-vessel | `VesselProfileLoaderTest` | ✅ 存在 |
| meta-claw-cli | `ConfigCommandTest` | ✅ 存在 |
| meta-claw-bootstrap | `MessageFlowIntegrationTest` | ✅ 存在 |
| meta-claw-store | — | ❌ 缺失 |

---

## 四、🔴 阻塞问题（必须优先修复）

### 4.1 VesselManager 无法加载 Vessel 配置

**严重级别**：🔴 高

**根因**：
- `VesselManager.loadVessels()` 从 `vessel.md` 用 SnakeYAML 解析 YAML frontmatter
- 但 `VesselTemplate` 创建的文件将 frontmatter 放在 `config.yaml`，`vessel.md` 只有 Markdown body
- 结果：Bootstrap 启动时 `VesselManager` 加载不到任何有效配置

**修复方案**：
1. 重构 `VesselManager.loadVessels()`，使其调用 `VesselConfigLoader.loadFromDirectory()`
2. 统一字段命名风格为下划线（与模板文件保持一致）
3. 删除 `VesselManager` 中冗余的 YAML 解析逻辑（`mapToVesselConfig`、`getString`、`getBoolean`、`getStringList`）

**预估工作量**：1-2 小时

### 4.2 模板文件拼写错误

**严重级别**：🟡 中

**根因**：`vessel-config.tmpl.yaml` 第 21 行 `provide:` 应为 `provider:`

**修复方案**：直接修正模板文件

**预估工作量**：5 分钟

---

## 五、下一步实施计划

### Phase 1 收尾（本周内完成）

目标：消除所有阻塞问题和已知不一致，使 Batch 1-3 真正达到 100%。

| # | 任务 | 优先级 | 预估工时 | 所属 Batch |
|---|------|--------|----------|------------|
| 1 | 重构 `VesselManager.loadVessels()` 复用 `VesselConfigLoader` | P0 | 2h | Batch 2 |
| 2 | 统一 `VesselManager` 字段映射为下划线命名 | P0 | 30min | Batch 2 |
| 3 | 修正 `vessel-config.tmpl.yaml` 拼写错误（`provide` → `provider`） | P0 | 5min | Batch 2 |
| 4 | 清理所有 Java 注释中的 "专家" / "Expert" 残留 | P1 | 1h | Batch 1 |
| 5 | `ChatCommand` 集成 `JsonlConversationStore`：启动加载历史、对话后追加 | P1 | 3h | Batch 3 |
| 6 | 补充 `JsonlConversationStore` 单元测试 | P1 | 2h | Batch 4 |
| 7 | 补充 `FilePreferenceStore` 单元测试 | P1 | 2h | Batch 4 |
| 8 | `mvn clean test` 全量验证 | P0 | 30min | — |

### Phase 2 启动（下周开始）

目标：完成 Prompt Engineering 与存储层完善。

| # | 任务 | 优先级 | 预估工时 | 所属 Batch |
|---|------|--------|----------|------------|
| 9 | 实现 `SystemPromptBuilder`：组装 identity + soul + capabilities + guidelines | P0 | 4h | Batch 5 |
| 10 | 实现 `TemplateLoader`：加载 `system.tmpl.md` + `context.tmpl.md` | P0 | 3h | Batch 5 |
| 11 | 实现 `PromptContext`：承载组装后的系统提示上下文 | P1 | 2h | Batch 5 |
| 12 | `MemoryManager`：上下文窗口截断策略（最近 N 轮 / Token 限制） | P1 | 4h | Batch 4 |
| 13 | `ChatCommand` 集成 `SystemPromptBuilder`，替换硬编码 system prompt | P1 | 2h | Batch 5 |
| 14 | `VesselRuntime` 集成 `SystemPromptBuilder` | P1 | 2h | Batch 5 |

### Phase 3（后续规划）

- Batch 6：工具引擎（`ToolRegistry`、`ToolExecutor`）
- Batch 7：MCP 客户端 + 技能系统
- Batch 8：高级功能（权限、查询引擎、Web 控制台）

---

## 六、风险与依赖

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| `VesselManager` 重构引入回归 | Bootstrap 启动失败 | 重构前补充 `VesselManagerTest`，覆盖 loadVessels 场景 |
| 下划线命名统一后，已有 config.yaml 不兼容 | 现有用户配置解析失败 | 保持向后兼容：优先读取下划线，回退读取驼峰 |
| Prompt Engineering 复杂度超预期 | Phase 2 延期 | 先实现 MVP（identity + capabilities 拼接），再逐步增强 |
| Spring AI 版本升级 | API 变化导致编译失败 | 锁定 Spring AI 1.1.4，升级时单独评估 |

---

## 七、文档变更记录

| 日期 | 文档 | 变更 |
|------|------|------|
| 2026-05-08 | `2026-05-08-meta-claw-vessel-migration-and-roadmap.md` | 初始版本 |
| 2026-05-09 | `2026-05-08-meta-claw-vessel-migration-and-roadmap.md` | v1.1 修订：补充 meta-claw-store、修正 Batch 完成度、添加已知问题章节 |
| 2026-05-09 | `2026-05-09-meta-claw-implementation-progress-and-next-steps.md` | 新建：当前进度报告与下一步计划（本文档） |

---

*报告人：Kimi Code CLI*  
*审计日期：2026-05-09*
