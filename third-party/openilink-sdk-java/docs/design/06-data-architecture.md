# 数据架构（Data Architecture）

该图展示 openilink-sdk-java 的数据流转和存储架构。

## 数据说明

SDK 主要处理实时消息数据，支持可选的本地持久化。

```mermaid
graph TD

subgraph Data_Source[数据源]
USER_MSG[用户消息]
BOT_MSG[Bot 消息]
MEDIA[媒体文件]
end

subgraph SDK_Processing[SDK 处理层]
RECEIVE[消息接收]
PARSE[消息解析]
CACHE[上下文缓存]
SEND[消息发送]
end

subgraph Memory_Storage[内存存储]
TOKEN_MAP[ContextToken Map]
SYNC_BUF[Sync Buffer]
end

subgraph Optional_Persistence[可选持久化]
LOCAL_FILE[本地文件]
REDIS[Redis Cache]
DB[数据库]
end

subgraph External_Storage[外部存储]
CDN[微信 CDN]
end

USER_MSG --> RECEIVE
RECEIVE --> PARSE
PARSE --> CACHE
CACHE --> TOKEN_MAP
CACHE --> SYNC_BUF
TOKEN_MAP --> SEND
SEND --> BOT_MSG
MEDIA --> CDN
CDN --> PARSE
SEND --> CDN
SYNC_BUF --> LOCAL_FILE
TOKEN_MAP --> REDIS
PARSE --> DB
```

## 数据类型

1. **消息数据**
   - 文本消息（TextItem）
   - 图片消息（ImageItem）
   - 语音消息（VoiceItem）
   - 文件消息（FileItem）
   - 视频消息（VideoItem）

2. **会话数据**
   - BotToken：Bot 认证令牌
   - ContextToken：用户会话上下文令牌
   - SyncBuffer：消息同步游标

3. **媒体数据**
   - 加密参数（AESKey）
   - CDN 查询参数
   - 文件元数据

## 数据持久化策略

- **内存优先**：ContextToken 和 SyncBuffer 默认存储在内存
- **可选持久化**：支持将 SyncBuffer 持久化到文件，实现断点续传
- **外部存储**：支持集成 Redis 或数据库存储会话数据
- **媒体存储**：所有媒体文件存储在微信 CDN，SDK 仅处理引用
