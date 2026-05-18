# 短期记忆接口统一设计

## 背景

短期记忆当前已经把磁盘格式统一成 `MemoryEntry`，但接口层仍混用 `SpiMessage`：

- `appendMessage` 直接接收 `SpiMessage`
- `getHistory` 直接返回 `SpiMessage`
- `truncateByRound` / `truncateByToken` 既是策略名，也是接口名

这会让 store 层继续感知 LLM 传输模型，和“短期、长期统一使用 `MemoryEntry`”的方向不一致。

## 目标

1. `ShortMemoryStore` 与 `ShortMemoryManager` 的出入参统一为 `MemoryEntry`
2. `truncateByRound` 更名为 `getHistory`
3. `truncateByToken` 更名为 `getHistoryByToken`
4. `MemoryEntry` 与 `SpiMessage` 的互转集中到独立工具类
5. store 层不再直接依赖 `SpiMessage`

## 设计

### 接口定义

`ShortMemoryStore` 调整为：

- `appendEntry(String sessionKey, MemoryEntry entry)`
- `List<MemoryEntry> getHistory(String sessionKey, int limit)`
- `List<MemoryEntry> getHistory(List<MemoryEntry> history, int maxRounds)`
- `List<MemoryEntry> getHistoryByToken(List<MemoryEntry> history, int maxTokens)`
- `String summarizeConversation(List<MemoryEntry> history)`

`ShortMemoryManager` 仅做同名透传，不再暴露 `SpiMessage`。

### 转换工具

新增 `MemoryEntryConverter`：

- `fromSpiMessage(String sessionId, SpiMessage message)`
- `toSpiMessage(MemoryEntry entry)`
- `toSpiMessages(List<MemoryEntry> entries)`

转换规则：

- `category = message`
- `metadata.role = SpiMessage.role`
- `metadata.toolCalls = SpiMessage.toolCalls`，仅在存在时写入
- 时间由 `fromSpiMessage` 生成

### 调用边界

- `ChatCommand` 保持面向 LLM 的 `SpiMessage` 列表
- 写入短期记忆前，先用 `MemoryEntryConverter.fromSpiMessage(...)`
- 恢复历史时，从 manager 取 `MemoryEntry`，再统一转回 `SpiMessage`
- 发送给 LLM 前，先把当前 `SpiMessage` 历史转成 `MemoryEntry` 做窗口裁剪，再转回 `SpiMessage`

### Store 实现

`JsonlShortMemoryStore` 只读写 `MemoryEntry`：

- 不再包含 `SpiMessage` 转换逻辑
- 继续兼容旧版磁盘文件读取，但兼容逻辑放在 converter 可复用的位置

## 非目标

- 本次不改长期记忆接口
- 本次不做旧文件批量迁移
- 本次不扩展新的 token 估算策略

## 验证

1. `MemoryEntryConverter` 有独立转换测试
2. `JsonlShortMemoryStoreTest` 全部切到 `MemoryEntry`
3. `ChatCommandTest` 保持历史恢复行为
4. `./init.sh` 通过
