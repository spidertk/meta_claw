# Memory 聚合根重构设计

## 背景

当前 `MemoryEntry` 同时承担了三种不同层级的职责：

- 单条短期消息
- 长期偏好记录
- 短期会话列表摘要

这会导致会话级字段如 `sessionId`、`updatedAt`、`messageCount` 被写进单条消息 JSON，也让短期会话摘要没有自然归属。

## 目标

1. 用 `Memory` 作为记忆领域聚合根
2. `Memory` 同时维护：
   - 短期会话列表
   - 每个短期会话的摘要
   - 长期偏好列表
3. 单条消息、会话聚合、长期偏好各自使用清晰的子对象
4. 短期会话目录下同时维护逐条消息和会话摘要

## 领域模型

### Memory

记忆聚合根：

- `List<SessionMemory> sessions`
- `List<PreferenceMemory> preferences`

### SessionMemory

短期会话聚合：

- `sessionId`
- `updatedAt`
- `messageCount`
- `summary`
- `List<MemoryMessage> messages`

### MemoryMessage

单条短期消息：

- `timestamp`
- `role`
- `content`
- `toolCalls`

### PreferenceMemory

长期偏好记录：

- `id`
- `timestamp`
- `category`
- `content`
- `metadata`

## 文件布局

每个会话目录下：

```text
conversations/<session-id>/
├── history.jsonl
└── summary.json
```

- `history.jsonl`：逐条 `MemoryMessage`
- `summary.json`：一个 `SessionMemory` 的摘要视图，不包含完整 `messages`

长期偏好继续存放在：

```text
preferences/preferences.jsonl
```

内容改为逐条 `PreferenceMemory`

## 接口边界

### 短期记忆

- `appendMessage(String sessionKey, MemoryMessage message)`
- `getHistory(String sessionKey, int limit)` 返回 `List<MemoryMessage>`
- `getHistoryByToken(String sessionKey, int maxTokens)` 返回 `List<MemoryMessage>`
- `listSessions(String vesselId)` 返回 `List<SessionMemory>`
- `loadSummary(String sessionKey)` / `saveSummary(String sessionKey, SessionMemory summary)`

### 长期记忆

- 长期偏好接口统一使用 `PreferenceMemory`

### 转换边界

- `MemoryMessageConverter` 负责 `SpiMessage <-> MemoryMessage`
- 不再让统一实体直接承接传输模型转换

## 非目标

- 本次不引入自动摘要算法
- 本次先建立 `summary.json` 的数据结构和读写能力
- 本次不做旧数据批量迁移，只做必要兼容读取

## 验证

1. 单条消息文件不再出现会话级字段
2. 会话列表返回 `SessionMemory`
3. 会话目录下可读写 `summary.json`
4. 长期偏好切换到 `PreferenceMemory`
5. `./init.sh` 通过
