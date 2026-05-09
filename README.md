# meta_claw

MetaClaw 是一个基于 Java 的 AI Agent 平台，采用模块化架构设计，支持多 Provider LLM 调用、流式响应、Vessel（智能体容器）配置管理等核心能力。

## 技术栈

- **Java 21**
- **Spring Boot 3.2.5**
- **Spring AI 1.1.4**（稳定版本）
- **Project Reactor**（Flux 流式处理）
- **Reactor Netty HTTP**（`WebClient` 底层，保障 SSE 流式响应不被缓冲）

## 模块说明

| 模块 | 说明 |
|------|------|
| `meta-claw-core` | 核心 SPI、LLM 客户端工厂、Spring AI ChatClient 封装、ExpertRuntime |
| `meta-claw-vessel` | Vessel 配置加载与解析、VesselProfile 管理 |
| `meta-claw-session` | Session 会话管理 |
| `meta-claw-gateway` / `meta-claw-gateway-weixin` | 网关层 |
| `meta-claw-bootstrap` | 启动模块 |
| `meta-claw-cli` | 命令行交互界面 |

## LLM Provider 支持

项目通过 `OpenAiChatClientFactory` 统一支持所有兼容 OpenAI API 协议的 Provider：

- OpenAI
- Moonshot（月之暗面）
- DeepSeek
- Azure OpenAI
- GitHub Models
- SiliconFlow、Volcengine、Baichuan、Zhipu、Qwen、DashScope 等

`meta-claw-core` 基于 **Spring AI 1.1.4** 稳定版 API，通过 `OpenAiApi.builder()` + `OpenAiChatModel.builder()` 编程式动态创建 `ChatClient`，无需依赖自动配置即可在运行时切换不同的 API Key、Base URL 和模型。

### 流式响应关键依赖

Spring AI `OpenAiApi` 内部使用 `WebClient` 发起 SSE 流式请求。`WebClient` 需要 **Reactor Netty HTTP** 作为底层 `ClientHttpConnector`，才能实现真正的异步流式传输：

- ✅ **有 `reactor-netty-http`**：SSE 数据逐帧实时推送，每个 chunk 间隔均匀，和 `curl` 行为一致
- ❌ **无 `reactor-netty-http`**：`WebClient` 回退到 JDK `HttpClient`，SSE 响应被全量缓冲，所有 chunk 在服务端完成后一次性到达，失去实时流式效果

因此 `meta-claw-core` 显式依赖 `reactor-netty-http`，确保流式对话的实时性。
