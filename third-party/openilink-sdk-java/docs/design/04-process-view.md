# 运行流程（Process View）

该图展示 openilink-sdk-java 运行时的数据流和交互流程。

## 流程说明

展示从扫码登录到消息收发的完整数据流转过程。

```mermaid
graph TD

subgraph User_Layer[用户层]
USER[用户]
APP[应用程序]
end

subgraph SDK_Layer[SDK 层]
LOGIN[LoginWithQR]
MONITOR[Monitor Loop]
HANDLER[MessageHandler]
PUSH[Push Message]
end

subgraph API_Layer[API 层]
QR[GET /get_bot_qrcode]
STATUS[GET /get_qrcode_status]
UPDATES[POST /getupdates]
SEND[POST /sendmessage]
end

subgraph Cache_Layer[缓存层]
TOKEN_CACHE[ContextToken Cache]
end

subgraph Server_Layer[服务器层]
ILINK[iLink Bot API]
end

USER --> APP
APP --> LOGIN
LOGIN --> QR
QR --> ILINK
ILINK --> STATUS
STATUS --> LOGIN
LOGIN --> TOKEN_CACHE
APP --> MONITOR
MONITOR --> UPDATES
UPDATES --> ILINK
ILINK --> HANDLER
HANDLER --> TOKEN_CACHE
TOKEN_CACHE --> PUSH
PUSH --> SEND
SEND --> ILINK
```

## 关键流程

1. **登录流程**：获取二维码 → 轮询扫码状态 → 获取 Token → 更新客户端配置
2. **监听流程**：长轮询 getUpdates → 接收消息 → 缓存 contextToken → 调用 Handler
3. **推送流程**：从缓存获取 contextToken → 发送消息 → 返回结果
