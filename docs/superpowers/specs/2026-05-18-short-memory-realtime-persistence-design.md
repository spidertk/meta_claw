# 短期记忆实时落盘设计

## 背景

当前 CLI 会话已经会在启动时创建 `history.jsonl`，但聊天过程中用户仍观察到消息文件要到程序退出后才可见或才有内容。同时，短期会话文件仍直接序列化 `SpiMessage`，没有记录逐条消息发生时间。

## 目标

1. 用户输入后，当前会话文件应立即追加对应记录。
2. AI 回复完成后，当前会话文件应立即追加对应记录。
3. `history.jsonl` 中每条消息都记录可读时间，格式固定为 `yyyy-MM-dd HH:mm:ss`。
4. 短期记忆持久化继续统一使用 `MemoryEntry`，不再直接把 `SpiMessage` 作为磁盘格式。

## 设计

### 持久化模型

短期消息写入 `history.jsonl` 时统一使用 `MemoryEntry`：

- `timestamp`：消息发生时间
- `category`：固定为 `message`
- `content`：消息正文
- `sessionId`：当前会话 ID
- `metadata.role`：`user` / `assistant` / `system`

文件中的时间序列化格式固定为 `yyyy-MM-dd HH:mm:ss`，便于人工直接查看。

### 读写边界

- 上层聊天流程继续使用 `SpiMessage`
- `JsonlShortMemoryStore.appendMessage()` 负责把 `SpiMessage` 转成 `MemoryEntry`
- `JsonlShortMemoryStore.getHistory()` 负责把 `MemoryEntry` 转回 `SpiMessage`
- 这样可以保持 LLM 传输模型和磁盘模型解耦，同时满足统一记忆实体要求

### 实时落盘

- 每次 `appendMessage()` 都在当前调用内完成文件追加
- 使用 `FileChannel.force(true)` 保持现有同步刷盘语义
- 新增测试直接读取 `history.jsonl`，验证追加完成后文件内容已经存在，而不是依赖进程退出

## 非目标

- 本次不调整长期记忆文件结构
- 本次不扩展 CLI 展示层，不在终端额外打印时间
- 本次不增加新的旁支测试，只补核心 P0 覆盖

## 验证

1. `JsonlShortMemoryStoreTest`
   - 初始化会话后创建空文件
   - 追加后立即能从文件中读到带格式化时间的 `MemoryEntry`
   - 读取历史时仍能恢复成原有 `SpiMessage`
2. `./init.sh`
   - 全仓编译通过
   - P0 测试集通过
