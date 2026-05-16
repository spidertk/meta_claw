# Meta-Claw Memory Domain 重构设计文档

> 日期：2026-05-16
> 目标：把 Memory 提升为独立一级领域，拆清短期记忆、长期记忆与会话生命周期，并让 core/store/prompt 三层结构共同反映这套模型。

---

## 一、问题定义

当前仓库已经有“会话历史”“用户偏好”“会话生命周期”三类能力，但它们的代码边界并未按领域组织：

```text
core/session
├─ ConversationStore
├─ ChatMessage
├─ UserPreferenceStore
├─ PreferenceEntry
└─ SessionManager / UserSession / SessionStorage

core/prompt
└─ ConversationHistoryManager（原 MemoryManager）

store/conversation
└─ JsonlConversationStore

store/preferences
└─ FilePreferenceStore
```

这造成两个问题：

1. `Memory` 作为产品概念没有对应的代码边界。
2. `session` 目录同时承载“会话生命周期”与“会话中保存了什么”，职责被混在一起。

## 二、目标模型

本次重构保留 `Memory` 作为上位概念，并把它正式落为独立领域：

```text
Memory
├─ Short-term Memory
│  └─ Conversation History
└─ Long-term Memory
   └─ Preferences
```

### 2.1 Memory

- 是独立一级领域，而不是 prompt 的附属概念。
- 当前阶段只包含已实现能力，不虚构尚未落地的长期记忆子类型。

### 2.2 Short-term Memory

- 表示当前会话或恢复中的消息历史。
- 当前职责包括：消息模型、历史持久化接口、过滤、统计、媒体引用、历史裁剪与摘要占位。

### 2.3 Long-term Memory

- 表示跨会话持续存在的长期信息。
- 当前已实现子类型只有 `Preferences`。
- 未来可扩展事实、关系、长期摘要、经验等能力。

### 2.4 Session

- 仅表示会话生命周期与运行容器。
- `SessionManager`、`UserSession`、`SessionStorage` 继续保留在 `session` 域。
- 它们负责“会话如何存在”，不负责“会话记住了什么”。

## 三、目标包结构

### 3.1 core 层

```text
meta.claw.core.memory
├─ shortterm
│  ├─ ChatMessage
│  ├─ ConversationHistoryManager
│  ├─ ConversationInfo
│  ├─ ConversationStats
│  ├─ ConversationStore
│  ├─ MediaReference
│  └─ MessageFilter
└─ longterm
   ├─ PreferenceEntry
   └─ UserPreferenceStore
```

```text
meta.claw.core.session
├─ ChatMode
├─ InMemorySessionStorage
├─ SessionManager
├─ SessionStorage
└─ UserSession
```

### 3.2 store 层

```text
meta.claw.store.memory
├─ shortterm
│  └─ JsonlConversationStore
└─ longterm
   └─ FilePreferenceStore
```

这样 core 与 store 会共享同一套领域边界，后续扩展不会再在包结构上二次迁移。

## 四、Prompt 分层

Prompt 只消费 memory，不再替 memory 定义概念：

```text
System Prompt
├─ Identity
├─ Tools
├─ Skills
└─ Domain Knowledge

Context
├─ Workspace
├─ Runtime
├─ User Preferences
└─ Conversation History
```

### 4.1 模板占位符

`context.tmpl.md` 使用显式占位符：

```markdown
<WORKSPACE_SECTION/>

<RUNTIME_SECTION/>

<PREFERENCES_SECTION/>

<CONVERSATION_HISTORY_SECTION/>
```

- 不再使用含混的 `<MEMORY_SECTION/>`。
- `User Preferences` 来自 long-term memory。
- `Conversation History` 来自 short-term memory。

## 五、迁移原则

1. **领域先于目录**：只有真正属于 memory 的类型才迁移。
2. **不扩大 session 语义**：session 继续是生命周期域，不吸收 memory。
3. **行为保持不变**：CLI 会话恢复、JSONL 存储、偏好读取在重构后维持现有行为。
4. **包结构镜像**：core 与 store 使用同一套 shortterm / longterm 分层，避免未来再拆。

## 六、范围边界

### 6.1 本次包含

- 建立独立 memory 域。
- 将短期记忆、长期偏好相关类型迁入新包。
- 将 store 实现同步迁入 memory 对应子包。
- 修正 prompt 模板与 section 命名。
- 更新相关 import、测试、文档与状态文件。

### 6.2 本次不包含

- 不实现新的长期记忆提取、检索、融合、写回能力。
- 不改变 CLI 会话恢复产品行为。
- 不重写 session 生命周期模块。
- 不更换现有 JSONL / 文件存储格式。

## 七、验证标准

1. 活跃源码中，memory 能作为独立包结构被清晰识别。
2. `session` 包只保留生命周期相关类型。
3. prompt 中明确区分 `User Preferences` 与 `Conversation History`，不再使用含混的 `Memory` section。
4. `JsonlConversationStore` 与 `FilePreferenceStore` 已迁入 `store.memory.shortterm / longterm`。
5. 相关单元测试、定向 Maven 测试、标准入口 `./init.sh` 全部通过。

## 八、后续演进

当新的长期记忆能力进入实现阶段时，可直接在既有树下扩展：

```text
memory/longterm
├─ preferences
├─ facts
├─ summaries
└─ relationships
```

这让 Memory 既保留用户易懂的整体概念，也具备继续生长的代码骨架。
