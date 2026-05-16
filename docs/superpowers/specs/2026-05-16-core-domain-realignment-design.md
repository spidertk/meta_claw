# Meta-Claw Core Domain Realignment 设计文档

> 日期：2026-05-16
> 目标：删除未进入主链路的旧 session 域，拆解含混的 model 杂物间，并把 Memory 重构为“Manager 编排 + Store Backend”架构。

---

## 一、问题定义

当前 `meta-claw-core` 虽已把 Memory 提升为独立领域，但仍有三类结构问题：

1. `core.session` 只被自身测试和 `AppConfig` 装配引用，没有进入当前真实消息主链路。
2. `core.model` 同时容纳配置对象与消息流对象，已经退化为“通用 DTO 目录”。
3. Memory 仍然由调用方直接接触具体 store，且 short-term memory 同时存在 `SpiMessage` 与 `ChatMessage` 两套消息模型，边界重复且含义模糊。

## 二、目标结构

### 2.1 core 顶层目录

```text
meta.claw.core
├─ config
│  ├─ GlobalConfig
│  ├─ ProviderConfig
│  ├─ VesselConfig
│  ├─ MemoryConfig
│  ├─ GlobalConfigLoader
│  └─ VesselConfigLoader
├─ message
│  ├─ Context
│  ├─ ContextType
│  ├─ Reply
│  └─ ReplyType
├─ memory
│  ├─ shortterm
│  │  ├─ ShortMemoryManager
│  │  ├─ ConversationHistoryManager
│  │  └─ ShortMemoryStore
│  └─ longterm
│     ├─ LongMemoryManager
│     ├─ LongMemoryStore
│     ├─ PreferenceEntry
│     └─ UserPreferenceStore
├─ prompt
├─ runtime
├─ events
├─ eventbus
└─ spi
```

### 2.2 store 层

```text
meta.claw.store.memory
├─ shortterm
│  └─ JsonlShortMemoryStore
└─ longterm
   └─ FileLongMemoryStore
```

### 2.3 删除的目录

```text
meta.claw.core.session
meta.claw.core.model
```

## 三、Memory 分层

Memory 采用三层调用关系：

```text
调用层
ChatCommand / PromptContextFactory
        │
        ▼
编排层
ShortMemoryManager / LongMemoryManager
        │
        ├─ 读取 MemoryConfig
        ├─ 选择 backend
        ▼
后端层
ShortMemoryStore / LongMemoryStore
        │
        ▼
JsonlShortMemoryStore / FileLongMemoryStore
```

### 3.1 Manager

Manager 是稳定的领域入口，不是对现有实现的薄包装。

#### ShortMemoryManager

- 对外只暴露 `SpiMessage`。
- 负责根据配置选择 short-term backend。
- 负责把历史加载、追加、清理与窗口裁剪组织成统一能力。
- 调用 `ConversationHistoryManager` 执行窗口策略。

#### LongMemoryManager

- 负责根据配置选择 long-term backend。
- 当前阶段只服务 preference 能力，但它代表的是长期记忆编排层，而不是某个文件实现。
- 后续事实、关系、长期摘要进入时，应继续挂在该编排层下。

### 3.2 Strategy

#### ConversationHistoryManager

- 是 short-term memory 下的“历史窗口策略实现”。
- 负责：
  - 按轮数截断
  - 按 token 粗估截断
  - 对话摘要占位
- 它不负责：
  - backend 选择
  - 文件读写
  - 会话恢复编排

### 3.3 Store Backend

#### ShortMemoryStore

- 以 `SpiMessage` 为核心 I/O 类型。
- 负责持久化与恢复，不负责裁剪策略。

#### LongMemoryStore

- 当前阶段可继续围绕 `PreferenceEntry` 工作。
- 负责持久化与检索，不负责 prompt 编排。

#### Concrete Stores

- `JsonlShortMemoryStore`：当前 short-term backend。
- `FileLongMemoryStore`：当前 long-term backend。

文件格式与路径结构是 backend 细节，不应再反向塑造 core 层公开模型。

## 四、统一模型边界

### 4.1 Short-term Memory

- 唯一公开消息模型：`SpiMessage`
- 删除：`ChatMessage`
- 逐步收回：`ConversationInfo`、`ConversationStats`、`MediaReference`、`MessageFilter` 等当前过度暴露的存储形态对象；若 CLI 仍需会话列表，可由 backend 返回最小必要投影，而不是把文件 DTO 固化成领域核心。

### 4.2 Long-term Memory

- 当前公开模型继续使用 `PreferenceEntry`
- 不在本次提前抽象统一 `MemoryEntry`
- 等事实、关系、摘要等第二种以上长期记忆形态真正出现时，再抽象统一父模型

## 五、配置依赖

新增 `MemoryConfig`，让 backend 选择从调用方代码中退出：

```java
public class MemoryConfig {
    private String shortTermStore = "jsonl";
    private String longTermStore = "file";
}
```

`VesselConfig` 持有 `MemoryConfig` 或等价 memory 配置段。后续切换为其他 backend 时，应只改配置和装配，不改 `ChatCommand` 等调用方。

## 六、删除与迁移范围

### 6.1 删除

- `core.session.*`
- `SessionManagerTest`
- `SessionConfig`
- `ChatMessage`
- `AppConfig` 中只服务于旧 session 域的 Bean

### 6.2 迁移

- `GlobalConfig`、`ProviderConfig`、`VesselConfig` → `core.config`
- `Context`、`ContextType`、`Reply`、`ReplyType` → `core.message`

### 6.3 重命名 / 重构

- `JsonlConversationStore` → `JsonlShortMemoryStore`
- `FilePreferenceStore` → `FileLongMemoryStore`
- `ConversationStore` → `ShortMemoryStore`
- 现有直接 `new store` 的调用，迁移到 manager 或 manager factory

## 七、范围边界

本次包含：

- core 顶层领域归位
- Memory manager/backend 分层
- short-term 模型统一到 `SpiMessage`
- 旧 session 域移除
- 当前 JSONL / file backend 重命名与接入

本次不包含：

- 新的长期记忆子类型
- 统一抽象 `MemoryEntry`
- 更换持久化格式
- 改变 CLI 会话恢复的用户行为

## 八、验证标准

1. `meta.claw.core.session` 与 `meta.claw.core.model` 不再存在。
2. `config`、`message`、`memory` 三个域职责清晰。
3. short-term 对外只暴露 `SpiMessage`，不再存在 `ChatMessage`。
4. `ShortMemoryManager` / `LongMemoryManager` 是实际编排入口，而不是空接口。
5. backend 选择由配置驱动，调用方不再直接依赖具体 store 类。
6. store 层 concrete implementation 已反映 backend 角色。
7. 相关测试与 `./init.sh` 全部通过。

## 九、后续演进

当新的 backend 或新的长期记忆形态进入时，扩展点应落在：

```text
Manager 选择策略
Store backend 实现
Long-term subtype
```

而不是重新把 DTO、文件结构和调用方耦回一起。这样 Memory 才不是一组类名，而是一条能继续生长的架构主干。
