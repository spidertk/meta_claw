# Meta-Claw Phase 1 收尾设计文档

> 日期：2026-05-09
> 目标：消除 Phase 1 所有遗留问题，修复阻塞缺陷，使 Batch 1-3 真正达到 100% 完成度
> 依赖前置文档：`2026-05-09-meta-claw-implementation-progress-and-next-steps.md`

---

## 一、设计概览与范围

### 1.1 背景

Phase 1（Batch 1-3）在实际代码中存在以下未完成项和阻塞问题：

1. **VesselManager 与 VesselConfigLoader 配置加载路径不一致**：`VesselManager` 仍试图从 `vessel.md` 解析 YAML frontmatter，但实际 frontmatter 已迁移到 `config.yaml`
2. **字段命名风格不统一**：`VesselManager` 使用驼峰命名，`VesselConfigLoader` 和模板使用下划线命名
3. **模板拼写错误**：`vessel-config.tmpl.yaml` 中 `provide:` 应为 `provider:`
4. **ChatCommand 未集成持久化**：对话历史仍仅存于内存 `ArrayList`
5. **Java 注释残留**：多处 "Expert" / "专家" 未清理
6. **Store 模块缺测试**：`JsonlConversationStore` 和 `FilePreferenceStore` 已实现但无单元测试

### 1.2 设计原则

- **测试先行**：重构 `VesselManager` 前先补单元测试，确保重构前后行为一致
- **模块分组推进**：配置加载层、存储层 + CLI、注释清理三条线独立推进，互不阻塞
- **最小侵入**：不新增模块或外部依赖，不修改 `VesselConfig` 模型字段
- **向后兼容**：公共 API（`VesselManager` 构造函数和方法签名）保持不变

### 1.3 范围边界

**包含**：
- `VesselManager` 重构与测试
- `VesselConfigLoader` 迁移到 `meta-claw-core`
- `ChatCommand` 集成 `JsonlConversationStore`
- `JsonlConversationStore` 与 `FilePreferenceStore` 单元测试
- 全项目 Java 注释清理
- 模板拼写修正

**不包含**：
- `SystemPromptBuilder`、`TemplateLoader`、`PromptContext` 等 Batch 5 功能
- `ToolRegistry`、`ToolExecutor` 等 Batch 6 功能
- `MemoryManager` 上下文截断（Phase 2）
- `ConversationStore` 接口扩展（如增加 `vesselId` 参数重载）

---

## 二、线 1 — 配置加载层重构设计

### 2.1 问题根因

`VesselManager`（位于 `meta-claw-core`）与 `VesselConfigLoader`（位于 `meta-claw-vessel`）存在**模块边界错位**：

- `VesselManager` 在 core 中自行解析 `vessel.md` 的 YAML frontmatter
- `VesselConfigLoader` 在 vessel 中解析 `config.yaml` + `vessel.md` 的 Markdown body
- `meta-claw-core` 不依赖 `meta-claw-vessel`，导致 `VesselManager` 无法复用 `VesselConfigLoader`
- 结果是两套解析逻辑、两种字段命名、两个文件来源，永远对不齐

### 2.2 核心决策：迁移 VesselConfigLoader 到 core

将 `VesselConfigLoader` 从 `meta-claw-vessel` 迁移到 `meta-claw-core`，使其成为 core 的基础配置加载能力，供 `VesselManager` 和 CLI 共同复用。

**迁移路径**：

```
原路径：meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java
新路径：meta-claw-core/src/main/java/meta/claw/core/config/VesselConfigLoader.java

测试迁移：
原路径：meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java
新路径：meta-claw-core/src/test/java/meta/claw/core/config/VesselConfigLoaderTest.java
```

**需要更新 import 的引用方**：
- `VesselConfigResolver`（`meta-claw-vessel`）→ 更新 import 路径
- `ListCommand`（`meta-claw-cli`）→ 更新 import 路径

### 2.3 测试先行：VesselManagerTest

在重构 `VesselManager` 之前，先基于**新格式**（`config.yaml` + `vessel.md`）编写 `VesselManagerTest`。

**测试场景**：

| 测试方法 | 描述 |
|----------|------|
| `loadVessels_shouldLoadFromConfigYamlAndVesselMd` | 创建 `vessels/test-vessel/config.yaml` + `vessels/test-vessel/vessel.md`，验证 `loadVessels()` 后 `getConfig("test-vessel")` 返回正确配置，且 body section（identity/soul/capabilities）被正确解析 |
| `loadVessels_shouldHandleMissingDirectory` | 传入不存在的目录，验证不抛异常、返回空列表 |
| `loadVessels_shouldSkipConfigWithoutId` | 创建无 `id` 字段的 config.yaml，验证该 vessel 被跳过 |
| `runtimeRegistration_shouldWork` | 验证 `registerRuntime` / `getRuntime` / `listAvailableVessels` / `hasVessel` 基本操作 |

**测试数据格式**（`config.yaml`）：
```yaml
id: test-vessel
name: Test Vessel
description: A test vessel
emoji: 🤖
model: gpt-4
system_prompt: You are a test assistant
preferences_enabled: true
role: member
auto_serve: false
exclude_tools: []
```

**测试数据格式**（`vessel.md`）：
```markdown
# Test Vessel

## Identity

Test identity content

## Soul

Test soul content

## Capabilities

Test capabilities content
```

重构前，上述测试会**失败**（因为当前 `VesselManager` 仍读取 `vessel.md` 的 frontmatter）。重构后通过。

### 2.4 VesselManager 重构

**删除的冗余方法**：
- `mapToVesselConfig(Map<String, Object>)` → 由 `VesselConfigLoader` 替代
- `getString(Map, String)` → 删除
- `getBoolean(Map, String)` → 删除
- `getStringList(Map, String)` → 删除

**重构后的 `loadVessels()`**：

```java
public void loadVessels() {
    VesselConfigLoader loader = new VesselConfigLoader();
    List<VesselConfig> loaded = loader.loadFromDirectory(Path.of(vesselsDir));
    for (VesselConfig config : loaded) {
        if (config.getId() != null && !config.getId().isEmpty()) {
            vessels.put(config.getId(), config);
            log.info("成功加载 Vessel 配置: {} ({})", config.getId(), config.getName());
        }
    }
}
```

**保持不变的部分**：
- 构造函数签名（`VesselManager()` 和 `VesselManager(String vesselsDir)`）
- `getConfig(String)`、`getRuntime(String)`、`registerRuntime(...)`、`listAvailableVessels()`、`hasVessel(String)`
- `vessels` 和 `runtimes` 两个 `ConcurrentHashMap`

### 2.5 字段命名统一

当前 `VesselManager.mapToVesselConfig()` 使用驼峰命名（`systemPrompt`、`preferencesEnabled`），而 `VesselConfigLoader` 和模板文件使用下划线命名（`system_prompt`、`preferences_enabled`）。

**决策**：统一为下划线命名。删除 `VesselManager` 的驼峰映射后，自然统一为 `VesselConfigLoader` 的下划线风格。

**向后兼容说明**：
- 旧版 `vessel.md` 中的驼峰 frontmatter 在新体系下不再被 `VesselManager` 读取（frontmatter 已迁移到 `config.yaml`）
- `config.yaml` 由 `VesselTemplate` 生成，本身就是下划线命名，不受影响
- 若用户有历史手写的驼峰命名 `config.yaml`，需手动迁移为下划线命名

### 2.6 模板修正

**文件**：`meta-claw-vessel/src/main/resources/templates/vessel-config.tmpl.yaml`

**变更**（第 21 行）：
```yaml
# 修改前
provide: moonshot      # ❌ 拼写错误

# 修改后
provider: moonshot     # ✅ 正确
```

**验证**：修正后重新执行 `meta-cli create test-vessel`，检查生成的 `config.yaml` 中 `provider` 字段正确且无 `provide` 字段。

---

## 三、线 2 — 存储层 + CLI 集成设计

### 3.1 模块依赖调整

当前 `meta-claw-cli` 未依赖 `meta-claw-store`。在 `meta-claw-cli/pom.xml` 中补充：

```xml
<dependency>
    <groupId>com.meta</groupId>
    <artifactId>meta-claw-store</artifactId>
</dependency>
```

### 3.2 ChatCommand 集成 JsonlConversationStore

#### 3.2.1 当前状态

`ChatCommand` 使用内存 `ArrayList<SpiMessage> history` 存储对话历史，程序退出即丢失，未使用已实现的 `JsonlConversationStore`。

#### 3.2.2 sessionKey 策略（区分场景）

| 场景 | sessionKey 生成规则 | 说明 |
|------|---------------------|------|
| **CLI 模式** | `UUID.randomUUID().toString()` | 每次启动 `meta-cli chat <name>` 生成新标识，代表一次独立对话 |
| **服务端模式**（Gateway） | `userId + ":" + source + ":" + channelType` | 基于上游会话 ID，保持跨消息的一致性 |

本设计仅涉及 **CLI 模式** 的集成。服务端模式的持久化由 Gateway/AgentLoop 在后续批次中集成。

#### 3.2.3 CLI 模式数据流

```
启动 chat → 生成 UUID sessionKey
         → 构造 JsonlConversationStore(vesselsDir)
         → 不加载历史（新会话），只注入 system prompt
         
用户输入 → SpiMessage.user(input)
         → 发送 LLM 请求前：store.appendMessage(sessionKey, userChatMessage)
         → LLM 流式响应 → onComplete 时：
           - 追加 assistant SpiMessage 到内存 history
           - store.appendMessage(sessionKey, assistantChatMessage)
         
/exit   → 退出，历史已按 sessionKey 持久化到
           vessels/{vesselId}/conversations/{uuid}/history.jsonl
/clear  → 调用 store.clearHistory(sessionKey) + 清空内存 history
```

#### 3.2.4 ChatMessage 构造

```java
ChatMessage userMsg = ChatMessage.builder()
    .sessionKey(sessionKey)
    .role("user")
    .content(input)
    .vesselName(vesselName)
    .timestamp(LocalDateTime.now())
    .build();

ChatMessage assistantMsg = ChatMessage.builder()
    .sessionKey(sessionKey)
    .role("assistant")
    .content(responseBuffer.toString())
    .vesselName(vesselName)
    .timestamp(LocalDateTime.now())
    .build();
```

#### 3.2.5 错误处理

- **Store 追加失败**：记录 `log.error`，不中断对话流程，降级为纯内存模式
- **启动加载历史失败**：回退到空 history + system prompt
- **`/clear` 清理失败**：记录 `log.warn`，至少清空内存 history

#### 3.2.6 已知限制（Phase 1 接受）

`JsonlConversationStore.resolveVesselId()` 当前从 `baseDir` 下取第一个 vessel 目录。多 vessel 场景下可能解析到错误的 vesselId，导致历史文件存放到非预期的 vessel 目录下。

**缓解**：当前 CLI 场景通常只有一个 vessel（`default`），此限制在 Phase 1 可接受。Phase 2 将通过扩展 `ConversationStore` 接口（增加带 `vesselId` 参数的重载方法）彻底解决。

### 3.3 Store 单元测试设计

#### 3.3.1 JsonlConversationStoreTest

位于 `meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreTest.java`。

| 测试方法 | 描述 |
|----------|------|
| `appendMessage_andGetHistory_roundTrip` | 写入 3 条消息，读取验证顺序、内容和字段完整性 |
| `getHistory_withLimit` | 写入 10 条，limit=5，验证只返回最近 5 条 |
| `getHistory_unlimited_shouldReturnAll` | limit=0，验证返回全部 |
| `clearHistory_shouldTruncate` | 写入后清空，验证读取为空 |
| `deleteConversation_shouldRemoveDir` | 写入后删除，验证会话目录不存在 |
| `conversationExists_shouldReturnCorrectly` | 存在/不存在两种场景 |
| `concurrentAppend_shouldNotCorrupt` | 多线程并发写入 100 条，验证最终完整性 |
| `listConversations_shouldReturnSortedByUpdatedAt` | 创建多个会话，验证按更新时间倒序排列 |

#### 3.3.2 FilePreferenceStoreTest

位于 `meta-claw-store/src/test/java/meta/claw/store/preferences/FilePreferenceStoreTest.java`。

| 测试方法 | 描述 |
|----------|------|
| `addAndLookupPreference_shouldMatch` | 添加偏好，按关键词查询，验证命中 |
| `lookupPreference_noMatch_shouldReturnEmpty` | 查询不存在的关键词，验证返回空列表 |
| `listRecentPreferences_withLimit` | 添加多条，验证 limit 截断为最近 N 条 |
| `listRecentPreferences_unlimited_shouldReturnAll` | limit=0，验证返回全部 |
| `deletePreference_shouldRemove` | 删除指定 ID，验证过滤生效 |
| `clearPreferences_shouldTruncate` | 清空后验证为空 |
| `addPreference_withMetadata_shouldPreserve` | 验证 metadata 字段序列化和反序列化正确 |

**测试基础设施**：所有测试使用 JUnit 5 的 `@TempDir` 创建临时目录，避免污染真实文件系统。

---

## 四、线 3 — 注释清理与模板修正

### 4.1 注释清理范围

**目标**：全项目 Java 源文件中，所有 "Expert" / "专家" 替换为 "Vessel" / "数字员工"。

**已定位的残留文件**：

| 文件 | 残留示例 | 替换为 |
|------|----------|--------|
| `UserSession.java` | "关联专家等信息" / "目标专家标识" / "单聊目标专家字段" | "关联 Vessel 等信息" / "目标 Vessel 标识" / "单聊目标 Vessel 字段" |
| `ChatMode.java` | "单个专家代理一对一" / "多个专家代理互动" | "单个数字员工一对一" / "多个数字员工互动" |
| `SessionManager.java` | "目标专家解析" / "绑定目标专家" / "可用专家列表" / "已绑定专家" | "目标 Vessel 解析" / "绑定目标 Vessel" / "可用 Vessel 列表" / "已绑定 Vessel" |
| `ChatMessage.java` | "关联的专家/代理名称" | "关联的数字员工/Vessel 名称" |
| `AppConfig.java` | "目标专家解析" | "目标 Vessel 解析" |
| `ChatCommand.java` | "inspired by expert_cli/cli.py" | "inspired by vessel_cli/cli.py" |

**清理方法**：
1. 逐文件替换，确保只改注释和 Javadoc，不改有效代码（变量名、类名已在前序批次中完成）
2. 替换后执行 `grep -r "专家\|Expert" --include="*.java"` 验证结果为空
3. 重点检查 `SessionManager.java`，其 Javadoc 中残留最多

### 4.2 模板修正

**文件**：`meta-claw-vessel/src/main/resources/templates/vessel-config.tmpl.yaml`

**变更**（第 21 行）：
```yaml
# 修改前
provide: moonshot

# 修改后
provider: moonshot
```

**验证**：修正后重新执行 `meta-cli create test-vessel`，检查生成的 `config.yaml` 中 `provider` 字段正确。

---

## 五、验收标准与回滚策略

### 5.1 各线验收标准

| 线 | 验收项 | 验证方式 |
|---|--------|----------|
| 线 1 | `VesselManagerTest` 全部通过 | `mvn test -pl meta-claw-core -Dtest=VesselManagerTest` |
| 线 1 | `VesselConfigLoaderTest` 迁移后全部通过 | `mvn test -pl meta-claw-core -Dtest=VesselConfigLoaderTest` |
| 线 1 | `VesselConfigResolver` 编译通过（import 已更新） | `mvn compile -pl meta-claw-vessel` |
| 线 1 | Bootstrap 启动时 VesselManager 能加载 default vessel | 运行 `MetaClawApplication`，日志出现"成功加载 Vessel 配置" |
| 线 2 | `JsonlConversationStoreTest` 全部通过 | `mvn test -pl meta-claw-store -Dtest=JsonlConversationStoreTest` |
| 线 2 | `FilePreferenceStoreTest` 全部通过 | `mvn test -pl meta-claw-store -Dtest=FilePreferenceStoreTest` |
| 线 2 | `meta-cli chat default` 对话后生成 `history.jsonl` | 手动验证：`ls ~/.meta-claw/vessels/default/conversations/` |
| 线 2 | `/clear` 命令同时清空持久化历史 | 手动验证：执行 `/clear` 后 `history.jsonl` 被截空 |
| 线 3 | `grep -r "专家\|Expert" --include="*.java"` 结果为空 | 脚本验证 |
| 线 3 | 模板生成的 `config.yaml` 含 `provider:` 而非 `provide:` | 手动验证 |
| **整体** | `mvn clean test` 全量通过 | 全量构建 |

### 5.2 回滚策略

所有变更集中在一个 commit 中，若引入严重回归，按以下优先级处理：

1. **VesselManager 重构有问题**：回退到旧的 `loadVessels()` 实现（保留备份代码片段），先恢复系统可用性
2. **ChatCommand 集成有问题**：注释掉 store 相关代码，回退到纯内存 history 模式
3. **模板修正有问题**：直接 revert 模板文件的修改

整体回滚：执行 `git revert <commit-hash>` 即可一键回退全部变更。

---

## 六、变更文件清单

### 新增文件

```
meta-claw-core/src/test/java/meta/claw/core/runtime/VesselManagerTest.java
meta-claw-core/src/test/java/meta/claw/core/config/VesselConfigLoaderTest.java
meta-claw-store/src/test/java/meta/claw/store/conversation/JsonlConversationStoreTest.java
meta-claw-store/src/test/java/meta/claw/store/preferences/FilePreferenceStoreTest.java
```

### 迁移文件

```
meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java
    → meta-claw-core/src/main/java/meta/claw/core/config/VesselConfigLoader.java

meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java
    → meta-claw-core/src/test/java/meta/claw/core/config/VesselConfigLoaderTest.java
```

### 修改文件

```
meta-claw-core/src/main/java/meta/claw/core/runtime/VesselManager.java        # 重构 loadVessels，删除冗余方法
meta-claw-cli/pom.xml                                                           # 添加 meta-claw-store 依赖
meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java                     # 集成 JsonlConversationStore
meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigResolver.java      # 更新 import 路径
meta-claw-cli/src/main/java/meta/claw/cli/ListCommand.java                     # 更新 import 路径
meta-claw-vessel/src/main/resources/templates/vessel-config.tmpl.yaml          # 修正 provide → provider

# 注释清理（以下文件仅修改注释/Javadoc）
meta-claw-core/src/main/java/meta/claw/core/session/UserSession.java
meta-claw-core/src/main/java/meta/claw/core/session/ChatMode.java
meta-claw-core/src/main/java/meta/claw/core/session/SessionManager.java
meta-claw-core/src/main/java/meta/claw/core/session/ChatMessage.java
meta-claw-bootstrap/src/main/java/meta/claw/app/AppConfig.java
meta-claw-cli/src/main/java/meta/claw/cli/ChatCommand.java
```

### 删除文件

```
meta-claw-vessel/src/main/java/meta/claw/vessel/VesselConfigLoader.java
meta-claw-vessel/src/test/java/meta/claw/vessel/VesselConfigLoaderTest.java
```

---

*文档版本：v1.0*  
*日期：2026-05-09*
