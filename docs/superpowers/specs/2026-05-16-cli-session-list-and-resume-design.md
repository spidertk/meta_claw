# CLI 会话列出与显式恢复设计

> 日期：2026-05-16  
> 范围：为 CLI 增加“发现历史会话”和“显式恢复指定会话”能力。

## 背景

当前 CLI 已经会把对话消息落盘到 `JsonlConversationStore`，但用户还无法通过 CLI：

1. 查看某个 Vessel 已有的历史会话
2. 指定恢复哪一条历史会话

`ChatCommand` 每次启动都会生成新的 `sessionKey`。单纯修改 key 生成规则并不能解决产品问题，因为用户仍然不知道自己在继续哪一段对话。

## 目标

提供一条完整、显式、可理解的恢复路径：

1. `vessel-cli sessions <vessel>`：列出某个 Vessel 的历史会话
2. `vessel-cli chat <vessel> --resume <session-id>`：恢复指定历史会话
3. 恢复后继续追加到同一份 `history.jsonl`

## 非目标

- 不做“默认继续最近会话”
- 不做会话重命名、删除、搜索
- 不做跨 Vessel 的统一会话视图
- 不改变现有新建会话的默认行为

## 用户体验

### 列出会话

```bash
vessel-cli sessions default
```

输出示意：

```text
Sessions for vessel 'default'

SESSION ID                           UPDATED AT           MESSAGES
8f7...                              2026-05-16 16:40     12
2aa...                              2026-05-16 15:03     4
```

### 恢复会话

```bash
vessel-cli chat default --resume 8f7...
```

恢复后：

- 读取该 session 的历史消息
- 将历史转换成 `SpiMessage`
- 在 system prompt 后注入既有 user / assistant 消息
- 后续新消息继续写回同一个 session

## 架构设计

### 1. 存储层

`JsonlConversationStore` 增加按 Vessel 过滤的列表能力：

- `listConversations(String vesselId)`

原因：

- 现有 `listConversations()` 是跨所有 Vessel 扫描
- `sessions <vessel>` 需要只展示当前 Vessel 的会话
- 显式的 Vessel 边界也能避免误恢复到别的 Vessel 的会话

### 2. CLI 命令层

新增：

- `SessionsCommand`

职责：

- 校验 Vessel 存在
- 调用 `JsonlConversationStore.listConversations(vesselName)`
- 按更新时间倒序打印会话列表

扩展：

- `ChatCommand`

新增参数：

- `--resume <session-id>`

行为：

- 未传 `--resume`：保留当前行为，生成新 UUID
- 传入 `--resume`：
  - 校验会话存在
  - 使用该 session id
  - 读取历史
  - 将可恢复的 user / assistant / tool 消息转换为 `SpiMessage`
  - system prompt 仍由当前 Vessel 配置重新生成，并置于历史首位

### 3. 历史恢复策略

- 不直接复用旧 system 消息，避免恢复时沿用过期配置
- 只恢复对话消息本身
- 若历史为空，则仍允许进入该 session，相当于继续一个空会话

## 错误处理

- Vessel 不存在：
  - `sessions` 与 `chat --resume` 都应给出明确错误
- session id 不存在：
  - `chat --resume` 输出未找到提示并退出
- 某条历史消息无法解析：
  - 继续依赖现有存储层跳过坏行，不让整段恢复失败

## 验证

### 单元测试

- `JsonlConversationStoreTest`
  - 新增按 Vessel 列表过滤测试
- `ChatCommand` 相关测试
  - 恢复指定 session 时不生成新 UUID
  - 恢复历史会进入请求上下文
- `SessionsCommandTest`
  - 列表输出只包含目标 Vessel 的会话

### 手动验证

1. 创建两个独立会话并发送消息
2. 运行 `vessel-cli sessions default`
3. 复制其中一个 session id
4. 运行 `vessel-cli chat default --resume <session-id>`
5. 确认新消息追加到原 `history.jsonl`

## 成功标准

- 用户不需要进入文件系统就能发现历史会话
- 用户可以显式选择要继续的会话
- 同一 Vessel 的会话恢复不会串到其他 Vessel
- 新建会话与恢复会话两条路径都保持清晰
