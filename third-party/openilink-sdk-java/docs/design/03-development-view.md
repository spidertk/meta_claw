# 开发视图（Development View）

该图展示 openilink-sdk-java 的代码模块结构和依赖关系。

## 模块说明

项目采用标准 Maven 结构，按功能划分模块，保持低耦合高内聚。

```mermaid
graph TD

subgraph SDK_Core[SDK 核心模块]
CLIENT[com.openilink.client]
AUTH[com.openilink.auth]
MONITOR[com.openilink.monitor]
end

subgraph Service_Module[服务模块]
MESSAGE[com.openilink.service.message]
MEDIA[com.openilink.service.media]
CONFIG[com.openilink.service.config]
end

subgraph Model_Module[模型模块]
TYPES[com.openilink.model.types]
REQUEST[com.openilink.model.request]
RESPONSE[com.openilink.model.response]
ERROR[com.openilink.model.error]
end

subgraph Util_Module[工具模块]
HTTP[com.openilink.http]
CACHE[com.openilink.cache]
HELPER[com.openilink.util]
end

subgraph Example_Module[示例模块]
ECHO[example.EchoBot]
end

CLIENT --> AUTH
CLIENT --> MONITOR
MONITOR --> MESSAGE
MESSAGE --> HTTP
MEDIA --> HTTP
CONFIG --> HTTP
AUTH --> HTTP
MESSAGE --> TYPES
MESSAGE --> REQUEST
MESSAGE --> RESPONSE
HTTP --> ERROR
MONITOR --> CACHE
ECHO --> CLIENT
```

## 模块职责

- **SDK 核心模块**：提供主要 API 接口和核心功能
- **服务模块**：封装各类业务服务逻辑
- **模型模块**：定义数据模型、请求响应结构
- **工具模块**：提供 HTTP 客户端、缓存等基础设施
- **示例模块**：提供使用示例和最佳实践
