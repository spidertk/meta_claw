# Meta-Claw Core Domain Realignment 设计文档

> 日期：2026-05-16
> 目标：删除当前未进入主链路的 session 域，拆解含混的 model 杂物间，并为短期/长期记忆建立正式 manager 抽象。

---

## 一、问题定义

当前 `meta-claw-core` 已经把 `Memory` 提升为独立领域，但核心包结构仍有两处明显错位：

1. `core.session` 当前只被自身测试和 `AppConfig` 装配引用，没有进入真实消息处理主链路。
2. `core.model` 同时容纳配置对象与消息流对象，职责不一致，已经退化为“通用 DTO 目录”。

此外，Memory 目前只有具体实现类 `ConversationHistoryManager`，还没有抽象出短期记忆与长期记忆的正式接口。

## 二、目标结构

### 2.1 core 顶层目录

```text
meta.claw.core
├─ config
│  ├─ GlobalConfig
│  ├─ ProviderConfig
│  ├─ VesselConfig
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
│  │  ├─ ChatMessage
│  │  ├─ ConversationInfo
│  │  ├─ ConversationStats
│  │  ├─ ConversationStore
│  │  ├─ MediaReference
│  │  └─ MessageFilter
│  └─ longterm
│     ├─ LongMemoryManager
│     ├─ PreferenceEntry
│     └─ UserPreferenceStore
├─ prompt
├─ runtime
├─ events
├─ eventbus
└─ spi
```

### 2.2 删除的目录

```text
meta.claw.core.session
```

`session` 当前没有承载正在使用的产品主链路能力，继续保留只会把未来的“显式 CLI 会话恢复”与旧的“用户路由会话状态”再次混淆。

## 三、领域边界

### 3.1 config

承载“系统如何被配置”的对象与加载器：

- `GlobalConfig`
- `ProviderConfig`
- `VesselConfig`
- `GlobalConfigLoader`
- `VesselConfigLoader`

### 3.2 message

承载“消息如何在系统中流动”的对象：

- `Context`
- `ContextType`
- `Reply`
- `ReplyType`

### 3.3 memory.shortterm

承载“当前会话窗口内记住什么、如何读写”的对象：

- `ShortMemoryManager`
- `ConversationHistoryManager`
- conversation 相关模型与存储接口

### 3.4 memory.longterm

承载“跨会话长期保留什么”的对象：

- `LongMemoryManager`
- `PreferenceEntry`
- `UserPreferenceStore`

当前 long-term memory 只实现了 preference 这一个子类型；本次不新增事实、关系、长期摘要等具体实现。

## 四、Memory 抽象

### 4.1 ShortMemoryManager

短期记忆接口描述当前窗口能力：

```java
public interface ShortMemoryManager {
    List<SpiMessage> truncateByRound(List<SpiMessage> history, int maxRounds);
    List<SpiMessage> truncateByToken(List<SpiMessage> history, int maxTokens);
    String summarizeConversation(List<ChatMessage> history);
}
```

`ConversationHistoryManager implements ShortMemoryManager`，保留当前行为不变。

### 4.2 LongMemoryManager

长期记忆接口只定义顶层角色，不在本次虚构尚未形成的业务方法。

```java
public interface LongMemoryManager {
}
```

原因：当前已实现的长期记忆能力只有 `Preferences` 的数据模型与存储接口；若现在强行给 `LongMemoryManager` 填充尚无共识的方法，后续事实记忆、关系记忆进入时反而容易被错误抽象锁死。

## 五、删除范围

本次删除：

- `ChatMode`
- `InMemorySessionStorage`
- `SessionManager`
- `SessionStorage`
- `UserSession`
- `SessionManagerTest`
- `SessionConfig`
- `AppConfig` 中仅用于旧 session 域的 Bean 与说明

## 六、迁移范围

本次迁移：

- `GlobalConfig`、`ProviderConfig`、`VesselConfig` → `core.config`
- `Context`、`ContextType`、`Reply`、`ReplyType` → `core.message`

本次不改变：

- CLI 会话恢复行为
- `JsonlConversationStore` 的文件布局与持久化语义
- Gateway / AgentLoop / VesselRuntime 的业务流程
- 长期记忆的具体功能能力

## 七、验证标准

1. `meta.claw.core.session` 不再存在。
2. `meta.claw.core.model` 不再存在。
3. `config`、`message`、`memory` 三个域各自只承载同类职责。
4. `ConversationHistoryManager` 已实现 `ShortMemoryManager`。
5. `LongMemoryManager` 已存在于 `memory.longterm`。
6. `AppConfig` 不再装配旧 session Bean。
7. 相关测试与 `./init.sh` 全部通过。

## 八、后续演进

这次重构完成后，`core` 顶层目录会从“历史堆积”转为“按领域生长”。后续若加入事实记忆、长期摘要或关系记忆，应继续沿 `memory.longterm` 扩展，而不是重新发明新的横向 `model` 或 `session` 杂项目录。
