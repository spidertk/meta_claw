# 记忆实体统一设计

## 目标

围绕统一的 `MemoryEntry` 实体收敛短期记忆与长期记忆，同时移除两个已经没有实际价值的抽象：

- 用 `MemoryEntry` 替代 `PreferenceEntry` 和 `SessionSummary`
- 删除 `ConversationHistoryManager`，把历史窗口能力下沉到短期记忆存储契约
- 删除 `UserPreferenceStore`，让提示词构建直接依赖 `LongMemoryStore`

## 当前状态

仓库已经具备一条有价值的 Memory 边界：

- `core.memory.shortterm` 管理会话历史
- `core.memory.longterm` 管理持久化偏好
- `ShortMemoryManager` 与 `LongMemoryManager` 负责选择已配置的 backend

目前仍有三个不必要的分裂点：

1. 短期会话列表返回 `SessionSummary`，长期记忆使用 `PreferenceEntry`
2. 短期窗口裁剪放在独立的 `ConversationHistoryManager` 中，但调用方只能通过 `ShortMemoryManager` 间接使用它
3. `UserPreferenceStore` 只是 `LongMemoryStore` 的别名接口

这些拆分增加了名称和跳转成本，却没有形成真正有价值的边界。

## 选定方案

保留现有的短期 / 长期领域分支，但让两边共用同一个记忆实体。

这样既能保留两类记忆不同的访问模式，又能去掉重复模型和空转接口。相比把两个 store 直接合并成一个过宽的 `MemoryStore`，这是更窄、更清晰的改动。

## 领域模型

在 `core.memory` 下引入统一实体 `MemoryEntry`。

`MemoryEntry` 作为短期与长期记忆共用的记录模型，包含：

- `id`
- `timestamp`
- `category`
- `content`
- `metadata`
- `sessionId`
- `updatedAt`
- `messageCount`

长期记忆分支主要使用 `id`、`timestamp`、`category`、`content`、`metadata` 等通用字段。

短期记忆分支在返回会话列表投影时使用 `sessionId`、`updatedAt`、`messageCount`。某个分支没有语义的字段可以为空。

当所有调用方都迁移到 `MemoryEntry` 后，删除 `PreferenceEntry` 与 `SessionSummary`。

## 短期记忆

`ShortMemoryStore` 成为持久化短期历史行为的归属边界，也包括窗口化读取能力。

该接口将承载：

- 追加消息
- 读取完整历史或受限历史
- 以 `List<MemoryEntry>` 形式列出会话
- 清空历史
- 判断会话是否存在
- 按轮数裁剪历史
- 按 token 估算裁剪历史
- 生成会话摘要

`JsonlShortMemoryStore` 直接实现这些能力。

删除 `ConversationHistoryManager`。`ShortMemoryManager` 继续作为 backend 选择器与委托层存在，但不再组合第二个历史策略对象。

如果现有 `getHistory(sessionKey, limit)` 与新的 store 级窗口读取能力存在重叠，需要一并收敛，让调用方只面对一条清晰的读取主路径，而不是两套仅有细微差别的 API。

## 长期记忆

`LongMemoryStore` 改为使用 `MemoryEntry`，不再使用 `PreferenceEntry`。

`LongMemoryManager` 继续负责选择并委托到已配置 backend，但只实现 `LongMemoryStore`。

删除 `UserPreferenceStore`，因为它没有提供超出 `LongMemoryStore` 的额外能力。

`PromptContextFactory` 直接依赖 `LongMemoryStore`，并继续把最近长期记忆渲染到 prompt 的 preferences 区域。

## 数据流

### 短期记忆

1. CLI 通过 `ShortMemoryManager` 追加 `SpiMessage`
2. `ShortMemoryManager` 委托给已选中的 `ShortMemoryStore`
3. 需要受限历史的调用方通过 store-backed API 获取结果
4. 会话列表返回 `MemoryEntry` 投影

### 长期记忆

1. 通过 `LongMemoryManager` 写入长期记忆条目
2. 已选中的 `LongMemoryStore` backend 持久化 `MemoryEntry`
3. `PromptContextFactory` 从 `LongMemoryStore` 读取最近条目
4. 最近条目的内容被格式化进 prompt context

## 错误处理

- 不支持的 backend 名称继续在 manager 层快速失败
- 对于缺失或不可读的持久化数据，store 实现继续沿用当前返回空集合的行为
- JSON 解析失败继续记录日志并跳过单条脏数据，而不是让整次读取失败
- 删除别名接口时，不能削弱提示词生成中现有的 null 与空值保护

## 测试策略

围绕受影响的边界更新并扩展测试：

- `JsonlShortMemoryStoreTest`
  - 会话列表改为返回 `MemoryEntry`
  - 按轮数与按 token 裁剪在下沉到 store 后保持行为一致
- `ShortMemoryManagerTest`
  - 删除 `ConversationHistoryManager` 后，委托关系仍保持正确
- `FileLongMemoryStoreTest`
  - 所有持久化长期记录改用 `MemoryEntry`
- `LongMemoryManagerTest`
  - 委托逻辑改用新的实体类型
- `PromptContextFactoryTest`
  - 通过 `LongMemoryStore` 仍能正确渲染偏好内容
- `SessionsCommandTest`
  - 改用 `MemoryEntry` 后，CLI 会话展示保持不变

验证顺序：

1. 运行 core、store、CLI 中与 memory 相关的定向 Maven 测试
2. 运行仓库级 `./init.sh`

## 非目标

- 不合并 `ShortMemoryStore` 与 `LongMemoryStore`
- 不重做磁盘目录布局
- 不新增超出当前短期 / 长期行为所需的记忆类别
- 不改变用户可见的 CLI 命令

## 预期结果

完成后：

- Memory 领域只暴露一个共享实体，而不是两个相互重叠的模型
- 短期历史策略归属于短期 store 边界
- 提示词构建依赖真实的长期记忆契约，而不是一个空的语义别名
- 当前 manager / backend 架构继续保留，但名称更少、透传层更少、理解成本更低
