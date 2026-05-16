# Meta-Claw Memory Model Clarification 设计文档

> 日期：2026-05-16
> 目标：把当前实现中的“记忆 / 会话历史 / 偏好”重新分层，建立可继续扩展的 Memory 模型，并让模板与代码名称反映真实语义。

---

## 一、问题定义

当前实现已经具备三类能力，但命名与模板分层混在一起：

- `MemoryManager` 实际负责的是当前会话历史的裁剪与摘要占位。
- `UserPreferenceStore` 负责的是跨会话保留的用户偏好。
- `context.tmpl.md` 使用 `<MEMORY_SECTION/>`，但模板真实需要表达的是若干不同来源的上下文，而不是一个含混的“记忆块”。

这种混用会让后来者误以为“记忆 = 会话管理”，也会让未来真正的长期记忆能力无处安放。

## 二、目标模型

本次修复保留 `Memory` 作为上位概念，但把层级拆清楚：

```text
Memory
├─ Short-term Memory
│  └─ Conversation History
└─ Long-term Memory
   └─ Preferences
```

### 2.1 Short-term Memory

- 含义：当前会话或恢复中的会话历史。
- 当前实现：消息回放、按轮数截断、按 token 粗估截断、对话摘要占位。
- 代码命名：不再把承载该职责的类直接称为笼统的 `MemoryManager`，改用能揭示职责的名称。

### 2.2 Long-term Memory

- 含义：跨会话持续存在的长期信息。
- 当前实现：只包含 `Preferences`。
- 未来扩展：可继续承载事实、长期摘要、关系、经验等其他 memory subtype。

### 2.3 Preferences

- 含义：长期记忆中的一个明确子类型，不等于全部长期记忆。
- 当前行为：继续由 `UserPreferenceStore` 读取并注入 prompt。

## 三、设计决策

### 3.1 代码命名

- 将 `MemoryManager` 重命名为 `ConversationHistoryManager`。
- 将相关测试同步重命名。
- 原因：当前类只管理会话历史，而不是整个 Memory 体系；使用职责名比使用抽象总称更精确。

### 3.2 Prompt 分层

`system` 与 `context` 的职责保持区分：

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

- `Domain Knowledge` 只承载 Vessel 的领域知识。
- `User Preferences` 从长期记忆分支注入，但在 prompt 中以真实名称出现。
- `Conversation History` 从短期记忆分支注入，明确表示最近对话与摘要。

### 3.3 模板占位符

将 `context.tmpl.md` 中的笼统占位符拆分为：

```markdown
<WORKSPACE_SECTION/>

<RUNTIME_SECTION/>

<PREFERENCES_SECTION/>

<CONVERSATION_HISTORY_SECTION/>
```

- 不再继续使用 `<MEMORY_SECTION/>`，因为它隐藏了不同来源与生命周期。
- `SystemPromptBuilder` 分别提供 `buildPreferencesSection()` 与 `buildConversationHistorySection()`。
- 当某个 section 没有内容时，继续返回空字符串，不输出空标题。

## 四、范围边界

### 4.1 本次包含

- Memory 模型的正式分层说明。
- 代码命名从“泛记忆”收敛到“会话历史”。
- Prompt 模板与 section builder 的语义纠偏。
- 相关测试、文档、状态文件同步更新。

### 4.2 本次不包含

- 不实现新的长期记忆检索、提取、总结或写回能力。
- 不改变现有 CLI 会话恢复行为。
- 不引入新的存储结构。
- 不把 Preferences 扩展成事实库或用户画像系统。

## 五、验证标准

1. 活跃代码与模板中，不再用含混的 `MemoryManager` / `<MEMORY_SECTION/>` 表示具体会话历史或偏好加载。
2. 提示词构建测试能够证明：
   - `Domain Knowledge` 与 `User Preferences` 分处不同 section；
   - `User Preferences` 出现在 `Context`；
   - 最近消息与摘要以 `Conversation History` 命名出现。
3. 相关测试全部通过。
4. 标准入口 `./init.sh` 通过，证明主路径未被破坏。

## 六、后续演进

当真正的长期记忆能力进入实现阶段时，应沿着既有模型扩展，而不是回头复用会话历史概念：

```text
Long-term Memory
├─ Preferences
├─ Facts
├─ Summaries
└─ Relationships
```

这样 `Memory` 作为产品语言能保持统一，底层实现又不会失去边界。
