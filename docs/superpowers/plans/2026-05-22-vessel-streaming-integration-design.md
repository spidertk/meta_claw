# Vessel 流式对话与 WebSocket 渠道集成设计

> 设计目标：将 Python `expert_runtime.py` 的 ReAct 流式循环模式移植到 Java 侧，并为 meta-claw 引入 WebSocket 实时推送渠道。
> 
> 日期：2026-05-22
> 状态：设计稿，待评审

---

## 1. 当前流式基础设施盘点

### 1.1 已有能力

| 组件 | 位置 | 现状 |
|------|------|------|
| `SpiStreamingCallback` | `meta-claw-core/src/main/java/meta/claw/core/llm/SpiStreamingCallback.java` | 已定义完整生命周期回调：`onStart()`、`onChunk(String chunk)`、`onToolCall(SpiToolCall toolCall)`、`onComplete(SpiChatResponse response)`、`onError(Throwable error)` |
| `LlmClientManager.chatStream()` | `meta-claw-core/src/main/java/meta/claw/core/runtime/LlmClientManager.java` | 已基于 Spring AI `ChatClient.prompt().stream().content()` 实现，使用 `Flux.doOnNext()` 逐 chunk 回调，最后 `.blockLast()` 阻塞等待完成。内部有完整的 TTFB / chunk 计数 / 耗时日志 |
| CLI 流式输出 | `meta-claw-cli` | `ChatCommand` 已使用 `chatStream()`，在终端实时打印 chunk |
| `SpiChatRequest` / `SpiChatResponse` | `meta-claw-core` | 已使用 Lombok `@Builder`，支持 `messages`、`tools`、`options` |

### 1.2 缺失环节

| 缺失项 | 影响 | 优先级 |
|--------|------|--------|
| `VesselRuntime` 无流式入口 | 上层 AgentLoop 无法获取 chunk，只能等待完整 Reply | P0 |
| `VesselRuntime` 无工具循环 | 当前 `chat()` 是单轮 LLM 调用，无 ReAct 多轮能力 | P0 |
| `AgentLoop` 无 chunk 事件 | 只能发布 `VesselResponseReady`（完整结果），无法做实时推送 | P0 |
| `Channel` 无 `sendChunk` 方法 | 所有渠道只能发完整 `Reply`，无法流式分片发送 | P1 |
| 无 WebSocket 渠道 | Web 端无法建立长连接接收流式输出 | P1 |
| `openilink-sdk-java` 不支持流式 | 微信渠道物理上无法分片推送，只能发完整消息（设计约束） | 约束 |

### 1.3 openilink 不支持流式的约束

`WeixinChannel` 基于 `ILinkClient.push(userId, content)`，底层是 HTTP REST 长轮询，**不存在 Server-Sent Events 或 WebSocket 通道**。这意味着：
- 微信渠道**无法真正流式推送**。
- 若未来需要微信"打字机效果"，只能在客户端做**分片模拟**（将完整文本切成多段，间隔发送）。
- 本设计的 Phase 3（可选）考虑此方案，但 Phase 1/2 不解决微信流式问题。

---

## 2. Python `_chat_with_tools()` 核心循环解析

### 2.1 逐行逻辑拆解

Python 侧核心循环位于 `expert/runtime/expert_runtime.py:486-657`：

```python
async def _chat_with_tools(self, message, ..., max_steps=500, 
                           stream_callback=None, thinking_callback=None, 
                           step_callback=None, ...):
    provider = self._get_provider()
    system_prompt = self._build_system_prompt()
    history = self._build_history(message, conversation_history, media_list)
    
    final_message = ""
    step_count = 0
    
    while step_count < max_steps:
        step_count += 1
        self.stats.total_steps += 1
        
        accumulated_text, accumulated_think = [], []
        
        # 1. 定义流式回调（每收到一个 token 片段即触发）
        def on_part(part):
            if isinstance(part, str) or hasattr(part, 'text'):
                accumulated_text.append(part)
                if stream_callback:
                    stream_callback(part)        # <-- 实时流式输出
            elif hasattr(part, 'think'):
                accumulated_think.append(part.think)
                if thinking_callback:
                    thinking_callback(part.think) # <-- 思考过程回调
        
        # 2. 执行单步（流式调用 LLM + 自动工具决策）
        step_result: StepResult = await kosong.step(
            chat_provider=provider,
            system_prompt=system_prompt,
            toolset=self.toolset,
            history=history,
            on_message_part=on_part,  # <-- 流式注入点
        )
        
        # 3. 记账与历史追加
        self.stats.add_usage(step_result.usage)
        history.append(step_result.message)
        
        # 4. 工具调用分支
        if step_result.tool_calls:
            self.stats.total_tool_calls += len(step_result.tool_calls)
            tool_results = await step_result.tool_results()  # <-- 同步阻塞执行工具
            for tool_result in tool_results:
                history.append(self._tool_result_to_message(tool_result))
            
            if step_callback:
                await step_callback({"step": step_count, "tool_calls": ...})
            continue  # <-- 关键：工具执行后继续循环，进入下一轮 LLM 流式调用
        
        # 5. 无工具调用：组装最终结果并退出
        final_message = "\n\n".join([...])
        break
    
    return final_message, self.stats
```

### 2.2 可移植到 Java 的设计模式

1. **每轮 LLM 都是独立流式连接**：`kosong.step()` 内部每次调用 LLM 都是一次新的流式请求，工具执行期间连接断开。Java 侧也应如此：每次 `chatStream()` 内部循环调用 `llmClientManager.chatStream()`，工具执行完后再开新连接。

2. **流式回调在工具循环中的位置**：`on_part` 嵌套在 `while` 循环内部，意味着**每轮 ReAct 步骤都有自己的流式回调实例**。用户看到的是"多段流式输出"，每段之间被工具执行的停顿隔开。

3. **工具执行是同步阻塞的**：`await step_result.tool_results()` 会等待所有工具执行完毕，期间没有流式输出。Java 侧对应：`ToolExecutor.execute()` 同步阻塞，阻塞期间回调静默。

4. **历史列表是状态核心**：`history` 在循环间持续累积，包含 user / assistant / tool 消息。Java 侧对应：`ShortMemoryManager` 已提供 `getHistory()` 和持久化能力，但当前 `VesselRuntime` 未在循环间维护历史，需要改造。

5. **统计信息在循环间累积**：`RuntimeStats` 跨步骤累加 token、步数、工具调用数。Java 侧需新增 `VesselExecutionMetrics`。

---

## 3. Java 流式集成方案

### 3.1 VesselRuntime 流式改造

#### 3.1.1 新增接口

```java
@Component
public class VesselRuntime {
    
    // 现有阻塞接口保持不变
    public Reply chat(String vesselId, String sessionId, String userMessage) { ... }
    
    // 新增流式接口
    public void chatStream(String vesselId, String sessionId, String userMessage, 
                           SpiStreamingCallback callback) {
        VesselToolLoop loop = new VesselToolLoop(
            vesselId, sessionId, userMessage,
            llmClientManager, toolExecutor, shortMemoryManager,
            promptContextManager, systemPromptBuilder,
            callback
        );
        loop.run(); // 同步阻塞，但 chunk 通过 callback 实时透出
    }
}
```

#### 3.1.2 VesselToolLoop 核心循环

```java
@Slf4j
public class VesselToolLoop {
    
    private static final int DEFAULT_MAX_STEPS = 500;
    
    private final String vesselId;
    private final String sessionId;
    private final String userMessage;
    private final LlmClientManager llmClientManager;
    private final ToolExecutor toolExecutor;
    private final ShortMemoryManager shortMemoryManager;
    private final SpiStreamingCallback callback;
    private final VesselExecutionMetrics metrics = new VesselExecutionMetrics();
    
    private int step = 0;
    private final List<SpiMessage> history = new ArrayList<>();
    
    public void run() {
        callback.onStart();
        
        // 初始化历史：系统提示词 + 短期记忆
        String systemPrompt = buildSystemPrompt(vesselId);
        history.addAll(llmClientManager.buildLlmRequest(vesselId, sessionId, systemPrompt));
        // 追加当前用户消息
        history.add(SpiMessage.user(userMessage));
        
        try {
            while (step < DEFAULT_MAX_STEPS) {
                step++;
                metrics.incrementSteps();
                
                // === 每轮都是独立的流式调用 ===
                StringBuilder contentBuilder = new StringBuilder();
                AtomicReference<SpiChatResponse> stepResponseRef = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);
                
                SpiChatRequest request = SpiChatRequest.builder()
                    .vesselName(vesselId)
                    .messages(new ArrayList<>(history))  // 快照，防止并发修改
                    .build();
                
                llmClientManager.chatStream(request, new SpiStreamingCallback() {
                    @Override
                    public void onStart() {
                        log.debug("[VesselToolLoop] Step {} stream started", step);
                    }
                    
                    @Override
                    public void onChunk(String chunk) {
                        contentBuilder.append(chunk);
                        callback.onChunk(chunk); // 透传给上层（AgentLoop / WebSocket）
                    }
                    
                    @Override
                    public void onToolCall(SpiToolCall toolCall) {
                        // 当前 LlmClientManager 的 chatStream 未解析 toolCall，
                        // 需在 onComplete 后统一处理
                        log.debug("[VesselToolLoop] Tool call received: {}", toolCall);
                    }
                    
                    @Override
                    public void onComplete(SpiChatResponse response) {
                        stepResponseRef.set(response);
                        latch.countDown();
                    }
                    
                    @Override
                    public void onError(Throwable error) {
                        latch.countDown();
                        callback.onError(error);
                    }
                });
                
                latch.await(); // 阻塞等待本轮流式完成
                
                SpiChatResponse response = stepResponseRef.get();
                if (response == null) {
                    throw new IllegalStateException("Stream completed without response at step " + step);
                }
                
                // 记账
                if (response.usage() != null) {
                    metrics.addUsage(response.usage());
                }
                
                // 将 assistant 消息加入历史
                String assistantContent = contentBuilder.toString();
                history.add(SpiMessage.assistant(assistantContent));
                
                // === 工具调用分支 ===
                if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                    metrics.addToolCalls(response.toolCalls().size());
                    log.info("[VesselToolLoop] Step {} executing {} tool(s)", step, response.toolCalls().size());
                    
                    // 同步执行工具（与 Python 的 await step_result.tool_results() 对应）
                    List<ToolResult> results = toolExecutor.execute(response.toolCalls());
                    
                    // 工具结果回注历史
                    for (ToolResult result : results) {
                        history.add(SpiMessage.tool(result.getContent()));
                    }
                    
                    // 继续下一轮循环（注意：callback 在此刻无输出，用户感知为"停顿"）
                    continue;
                }
                
                // === 无工具调用：最终回复 ===
                callback.onComplete(response);
                break;
            }
        } catch (Exception e) {
            log.error("[VesselToolLoop] Error at step {}: {}", step, e.getMessage(), e);
            callback.onError(e);
        }
        
        // 持久化会话历史
        shortMemoryManager.appendHistory(vesselId, sessionId, history);
    }
}
```

#### 3.1.3 关键设计决策

1. **`chatStream` 是同步方法，但内部 chunk 是异步回调**：与 Python `chat()` 处理 event loop 冲突的方式类似——对外提供同步阻塞语义（调用方 `run()` 直到 ReAct 完成），但通过 `SpiStreamingCallback` 实时透出 chunk。WebSocket 推送方需要在 callback 内部做非阻塞发送。

2. **每轮历史快照**：`new ArrayList<>(history)` 传入 `SpiChatRequest`，避免 `LlmClientManager` 消费时与循环并发修改。

3. **`CountDownLatch` 等待单轮流式结束**：这是 Java 侧对 Python `await kosong.step()` 的等价物。Spring AI `blockLast()` 已在 `LlmClientManager.chatStream()` 内部完成，此处 latch 是等待 callback 链完成。

4. **工具执行期间无流式**：与 Python 完全一致。若需 UX 优化，可在工具执行前通过 callback 发送一个特殊标记（如 `<thinking>正在执行工具...</thinking>`），但本设计保持最小侵入。

### 3.2 AgentLoop 流式事件增强

#### 3.2.1 新增 Chunk 事件

```java
package meta.claw.core.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import meta.claw.core.message.Context;

/**
 * Vessel 流式回复 Chunk 事件
 * 当 Vessel 以流式模式处理用户消息时，每收到一个文本 chunk 即触发一次。
 * 用于 WebSocket 等支持实时推送的渠道。
 */
@Getter
@AllArgsConstructor
public class VesselResponseChunkEvent {
    
    /** 渠道类型 */
    private final String channelType;
    
    /** 当前文本 chunk */
    private final String chunk;
    
    /** 会话 ID */
    private final String sessionId;
    
    /** 消息上下文 */
    private final Context context;
    
    /** 是否为最后一个 chunk（流式结束标记） */
    private final boolean last;
    
    /** 当前 ReAct 步骤序号（用于调试） */
    private final int step;
}
```

#### 3.2.2 AgentLoop 双路径改造

```java
@Subscribe
public void onUserMessage(UserMessageReceived event) {
    Context context = event.getContext();
    String sessionId = event.getSessionId();
    String channelType = event.getChannelType();
    
    try {
        String targetVesselId = determineTargetVessel();
        VesselRuntime runtime = vesselManager.getRuntime(targetVesselId);
        
        // 判断当前渠道是否支持流式
        boolean streamingSupported = isStreamingSupported(channelType);
        
        if (streamingSupported) {
            // === 流式路径 ===
            runtime.chatStream(targetVesselId, sessionId, context.getContent(), 
                new SpiStreamingCallback() {
                    @Override
                    public void onStart() {
                        log.info("[AgentLoop] Stream started for session={}", sessionId);
                    }
                    
                    @Override
                    public void onChunk(String chunk) {
                        // 发布 chunk 事件，由 WebSocketChannel 订阅并推送
                        eventBus.post(new VesselResponseChunkEvent(
                            channelType, chunk, sessionId, context, false, -1));
                    }
                    
                    @Override
                    public void onToolCall(SpiToolCall toolCall) {
                        // 工具调用信息可通过特殊 chunk 或独立事件发送
                    }
                    
                    @Override
                    public void onComplete(SpiChatResponse response) {
                        // 发送结束标记
                        eventBus.post(new VesselResponseChunkEvent(
                            channelType, "", sessionId, context, true, -1));
                        
                        // 同时发布完整回复事件（兼容非流式下游）
                        Reply reply = new Reply(ReplyType.TEXT, response.content());
                        eventBus.post(new VesselResponseReady(channelType, reply, context));
                    }
                    
                    @Override
                    public void onError(Throwable error) {
                        log.error("[AgentLoop] Stream error: {}", error.getMessage(), error);
                        Reply errorReply = new Reply(ReplyType.ERROR, 
                            "流式处理异常: " + error.getMessage());
                        eventBus.post(new VesselResponseReady(channelType, errorReply, context));
                    }
                });
        } else {
            // === 非流式路径（保持现有逻辑） ===
            Reply reply = runtime.chat(targetVesselId, sessionId, context.getContent());
            eventBus.post(new VesselResponseReady(channelType, reply, context));
        }
        
    } catch (Exception e) {
        log.error("处理用户消息异常: sessionId={}", sessionId, e);
        Reply errorReply = new Reply(ReplyType.ERROR, "消息处理异常，请稍后重试");
        eventBus.post(new VesselResponseReady(channelType, errorReply, context));
    }
}

private boolean isStreamingSupported(String channelType) {
    // WebSocket 支持流式；微信不支持
    return "websocket".equals(channelType);
}
```

### 3.3 Channel 接口扩展

#### 3.3.1 Channel 增加默认方法

```java
public interface Channel {
    
    String getChannelType();
    void startup() throws Exception;
    default void stop() {}
    void handleText(ChatMessage msg);
    void send(Reply reply, Context context);
    
    /**
     * 发送流式 chunk（可选实现）
     * 不支持流式的渠道保持默认空实现即可。
     */
    default void sendChunk(String chunk, Context context) {
        // 默认空实现：非流式渠道无需处理
    }
    
    /**
     * 发送流式结束标记（可选实现）
     */
    default void sendChunkEnd(Context context) {
        // 默认空实现
    }
}
```

#### 3.3.2 WeixinChannel 不实现 sendChunk

`WeixinChannel` 继承 `ChatChannel` → `Channel`，因 `openilink` 限制，**不覆盖 `sendChunk`**，保持默认空实现。所有消息仍通过 `send(Reply, Context)` 完整发送。

#### 3.3.3 Gateway 订阅 Chunk 事件

```java
@Subscribe
public void onResponseChunk(VesselResponseChunkEvent event) {
    String channelType = event.getChannelType();
    Channel channel = registry.get(channelType);
    
    if (channel != null) {
        if (event.isLast()) {
            channel.sendChunkEnd(event.getContext());
        } else {
            channel.sendChunk(event.getChunk(), event.getContext());
        }
    } else {
        log.warn("[Gateway] 未找到对应渠道，无法发送 chunk, channelType={}", channelType);
    }
}
```

### 3.4 WebSocket 渠道设计

#### 3.4.1 模块选址

建议新增模块 **`meta-claw-gateway-web`**，与 `meta-claw-gateway-weixin` 同级。若项目当前阶段希望减少模块数量，也可暂放在 `meta-claw-gateway` 模块内，作为子包 `meta.claw.gateway.web`。

#### 3.4.2 技术选型

- **Spring WebFlux WebSocketHandler**：与现有 Spring Boot 技术栈一致，无需引入额外服务器。
- **Reactive 流对接**：WebSocket 出站本质是 `Flux<WebSocketMessage>`，与 Spring AI `Flux<ChatResponse>` 天然适配。

#### 3.4.3 WebSocketChannel 实现

```java
package meta.claw.gateway.web;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.message.Context;
import meta.claw.core.message.Reply;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.Channel;
import meta.claw.gateway.channel.ChatMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 渠道实现
 * 管理 WebSocket 会话生命周期，将入站消息转为 UserMessageReceived 事件，
 * 并将 VesselResponseChunkEvent 推送到对应会话。
 */
@Slf4j
@Component
public class WebSocketChannel implements Channel {
    
    /** sessionId -> WebSocketSession 映射 */
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    
    /** sessionId -> 该会话的 chunk Sink（用于流式推送） */
    private final ConcurrentHashMap<String, Sinks.Many<String>> chunkSinks = new ConcurrentHashMap<>();
    
    private final Gateway gateway;
    private final ObjectMapper objectMapper;
    
    public WebSocketChannel(Gateway gateway, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String getChannelType() {
        return "websocket";
    }
    
    @Override
    public void startup() {
        log.info("[WebSocketChannel] WebSocket 渠道已启动，等待连接...");
        // WebSocket 服务器由 Spring WebFlux 自动启动，此处无需额外操作
    }
    
    /**
     * 注册新 WebSocket 会话（由 WebSocketHandler 调用）
     */
    public void registerSession(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
        chunkSinks.put(sessionId, Sinks.many().unicast().onBackpressureBuffer());
        log.info("[WebSocketChannel] 会话已注册: sessionId={}", sessionId);
    }
    
    /**
     * 注销会话
     */
    public void unregisterSession(String sessionId) {
        sessions.remove(sessionId);
        Sinks.Many<String> sink = chunkSinks.remove(sessionId);
        if (sink != null) {
            sink.tryEmitComplete();
        }
        log.info("[WebSocketChannel] 会话已注销: sessionId={}", sessionId);
    }
    
    @Override
    public void handleText(ChatMessage msg) {
        // WebSocket 入站消息通过 Gateway 进入 EventBus
        gateway.onInboundMessage(msg, getChannelType());
    }
    
    @Override
    public void send(Reply reply, Context context) {
        // 非流式场景：发送完整消息（兜底）
        String sessionId = context.getSessionId();
        WebSocketSession session = sessions.get(sessionId);
        if (session == null) {
            log.warn("[WebSocketChannel] 会话不存在，无法发送: sessionId={}", sessionId);
            return;
        }
        
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "type", "complete",
                "content", reply.getContent()
            ));
            session.send(Mono.just(session.textMessage(payload))).subscribe();
        } catch (Exception e) {
            log.error("[WebSocketChannel] 发送完整消息失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void sendChunk(String chunk, Context context) {
        String sessionId = context.getSessionId();
        Sinks.Many<String> sink = chunkSinks.get(sessionId);
        if (sink == null) {
            return;
        }
        
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "type", "chunk",
                "content", chunk
            ));
            sink.tryEmitNext(payload);
        } catch (Exception e) {
            log.error("[WebSocketChannel] 发送 chunk 失败: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public void sendChunkEnd(Context context) {
        String sessionId = context.getSessionId();
        Sinks.Many<String> sink = chunkSinks.get(sessionId);
        if (sink == null) {
            return;
        }
        
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                "type", "end"
            ));
            sink.tryEmitNext(payload);
            // 注意：不关闭 sink，会话可继续下一轮对话
        } catch (Exception e) {
            log.error("[WebSocketChannel] 发送结束标记失败: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 获取指定会话的出站 Flux（由 WebSocketHandler 订阅）
     */
    public Flux<String> getOutboundFlux(String sessionId) {
        Sinks.Many<String> sink = chunkSinks.computeIfAbsent(sessionId, 
            k -> Sinks.many().unicast().onBackpressureBuffer());
        return sink.asFlux();
    }
}
```

#### 3.4.4 WebSocketHandler（Spring WebFlux）

```java
@Component
public class ChatWebSocketHandler implements WebSocketHandler {
    
    @Autowired
    private WebSocketChannel webSocketChannel;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = extractSessionId(session);
        webSocketChannel.registerSession(sessionId, session);
        
        // 入站：接收用户消息
        Mono<Void> inbound = session.receive()
            .map(WebSocketMessage::getPayloadAsText)
            .flatMap(payload -> {
                try {
                    JsonNode node = objectMapper.readTree(payload);
                    String type = node.get("type").asText();
                    String content = node.get("content").asText();
                    
                    if ("chat".equals(type)) {
                        ChatMessage msg = new ChatMessage();
                        msg.setContent(content);
                        msg.setOtherUserId(sessionId);
                        webSocketChannel.handleText(msg);
                    }
                } catch (Exception e) {
                    log.error("解析 WebSocket 消息失败: {}", e.getMessage(), e);
                }
                return Mono.empty();
            })
            .then();
        
        // 出站：订阅 chunk Sink
        Flux<WebSocketMessage> outbound = webSocketChannel.getOutboundFlux(sessionId)
            .map(session::textMessage);
        
        // 双边驱动
        return Mono.zip(inbound, session.send(outbound)).then()
            .doFinally(sig -> webSocketChannel.unregisterSession(sessionId));
    }
    
    private String extractSessionId(WebSocketSession session) {
        // 优先从 URL query param 获取，否则生成 UUID
        URI uri = session.getHandshakeInfo().getUri();
        String query = uri.getQuery();
        if (query != null && query.contains("sessionId=")) {
            return Arrays.stream(query.split("&"))
                .filter(p -> p.startsWith("sessionId="))
                .findFirst()
                .map(p -> p.substring("sessionId=".length()))
                .orElse(UUID.randomUUID().toString());
        }
        return UUID.randomUUID().toString();
    }
}
```

#### 3.4.5 消息格式定义

```json
// 入站（客户端 -> 服务端）
{
  "type": "chat",
  "sessionId": "user-123",
  "content": "你好"
}

// 出站 chunk（服务端 -> 客户端）
{
  "type": "chunk",
  "content": "你",
  "step": 1
}
{
  "type": "chunk",
  "content": "好",
  "step": 1
}

// 出站结束标记
{
  "type": "end",
  "step": 1
}

// 工具执行标记（可选扩展）
{
  "type": "tool_start",
  "toolName": "search",
  "step": 1
}
```

### 3.5 流式与工具循环的协同

#### 3.5.1 Spring AI stream() + tool calling 的行为

Spring AI 的 `ChatClient.stream()` 在启用 tool calling 时，行为取决于 provider 实现：
- 某些 provider（如 OpenAI）会在流式输出中穿插 `tool_calls` JSON，此时流会暂停，等待工具结果后再继续。
- 但更可控的模式是**显式分步**：先流式获取 assistant 的文本 + tool_calls，断开流，执行工具，再开新流。

本设计选择**显式分步模式**，与 Python `kosong.step()` 语义一致：

```
[用户消息]
   ↓
[流式连接 #1] → chunk1 → chunk2 → ... → tool_calls
   ↓ （断开）
[同步工具执行] → ToolResult(s)
   ↓
[流式连接 #2] → chunk1 → chunk2 → ... → final_text
   ↓ （断开）
[完成]
```

#### 3.5.2 用户侧感知

Web 端用户看到的是：
1. 第一段文字实时出现（如"我来帮您查一下..."）
2. 停顿（工具执行中，可显示"执行工具中..."动画）
3. 第二段文字实时出现（工具结果后的总结）

这与 Python CLI 侧的 `stream_callback` 体验完全一致。

#### 3.5.3 与 Python 的 `kosong.step()` 对比

| 维度 | Python `kosong.step()` | Java `VesselToolLoop` |
|------|------------------------|----------------------|
| 流式连接生命周期 | 单次 `step()` 内自动管理 | 每次 `chatStream()` 调用显式管理，`blockLast()` 后断开 |
| 工具执行阻塞 | `await step_result.tool_results()` | `toolExecutor.execute()` 同步阻塞 |
| 历史维护 | 调用方 `history.append()` | 调用方 `history.add()` |
| 统计累积 | `self.stats.add_usage()` | `metrics.addUsage()` |
| 重试逻辑 | 媒体附件失败时自动重试（无图片） | 暂不在 P1 实现，可在 `LlmClientManager` 层统一添加 |

---

## 4. 与 Python 设计的映射关系

| Python 概念 | Java 对应 | 说明 |
|------------|----------|------|
| `_chat_with_tools()` | `VesselToolLoop` | ReAct 循环核心。Python 是 `ExpertRuntime` 内部方法，Java 提取为独立类以便单测和复用 |
| `stream_callback` | `SpiStreamingCallback.onChunk()` | 文本流式回调。Python 接收 `str` / `TextPart`，Java 接收 `String chunk` |
| `thinking_callback` | `SpiStreamingCallback.onThinking()` | **需扩展 SPI**：新增 `void onThinking(String thinking)`，用于 Reasoning 模型（如 DeepSeek-R1） |
| `step_callback` | `VesselResponseChunkEvent` + 调试标记 | Python 的 `step_callback` 用于 CLI debug 模式；Java 侧可通过事件总线广播步骤信息，或增加 `VesselResponseChunkEvent.step` 字段 |
| `RuntimeStats` | `VesselExecutionMetrics` | 运行时统计：totalTokens、totalSteps、totalToolCalls、sessionStart |
| `ToolRunner` / `toolset` | `ToolExecutor` | 工具执行器（已存在，需确认是否支持批量 `execute(List<SpiToolCall>)`） |
| `memory_manager` | `ShortMemoryManager` | 短期记忆管理器（已存在）。当前 `VesselRuntime` 未充分利用，需在 `VesselToolLoop` 中接入 `appendHistory` |
| `_build_history()` | `LlmClientManager.buildLlmRequest()` | 历史构建。Python 支持 media_list（图片/音频/视频），Java 当前仅支持文本，需后续扩展 |
| `chat()` sync | `VesselRuntime.chat()` | 保持现有阻塞接口，返回完整 `Reply` |
| `chat_async()` | `VesselRuntime.chatStream()` | 新增流式接口。注意：Java 侧是"同步方法 + 异步回调"，而非 Python 的 `async def` |
| `on_part` 内联回调 | `SpiStreamingCallback` 匿名实现 | Python 在循环内定义 `on_part` 闭包，Java 在循环内创建匿名 `SpiStreamingCallback` |
| `_message_parts_to_str()` | `contentBuilder.toString()` | Python 处理 TextPart / ThinkPart 拼接，Java 侧由 Spring AI `content()` 直接返回字符串 |

---

## 5. 实施路径

### Phase 1：VesselRuntime.chatStream() + VesselToolLoop（核心，1-2 天）

**目标**：让 VesselRuntime 具备 ReAct 流式能力，CLI 可立即受益。

1. 新增 `VesselExecutionMetrics.java`（统计类）
2. 新增 `VesselToolLoop.java`（核心循环）
3. 改造 `VesselRuntime.java`：
   - 增加 `chatStream()` 方法
   - 现有 `chat()` 保持不变，内部可复用 `VesselToolLoop` 但不用流式回调
4. 验证：CLI 侧将 `ChatCommand` 从 `LlmClientManager.chatStream()` 迁移到 `VesselRuntime.chatStream()`，确认终端仍能实时打印 chunk

**验收标准**：
- `VesselToolLoop` 单测通过（mock LLM + mock ToolExecutor，验证循环次数、历史累积、callback 触发次数）
- CLI `ChatCommand` 流式输出正常

### Phase 2：AgentLoop Chunk 事件 + WebSocketChannel（1-2 天）

**目标**：Web 端可实时接收流式输出。

1. 新增 `VesselResponseChunkEvent.java`
2. 改造 `AgentLoop.onUserMessage()`：增加流式分支判断
3. 扩展 `Channel` 接口：增加 `sendChunk()` / `sendChunkEnd()` 默认方法
4. 新增 `WebSocketChannel.java` + `ChatWebSocketHandler.java`
5. 改造 `Gateway.java`：订阅 `VesselResponseChunkEvent`
6. 新增 `meta-claw-gateway-web` 模块（或放在 `meta-claw-gateway` 内）

**验收标准**：
- 前端 WebSocket 客户端连接到 `/ws/chat?sessionId=xxx`
- 发送消息后，可在浏览器 Console 中观察到连续的 `chunk` 消息
- 微信渠道不受影响（仍走 `VesselResponseReady` 完整路径）

### Phase 3：微信分片模拟（可选，0.5 天）

**目标**：在微信端模拟"打字机效果"。

1. 在 `WeixinChannel` 中新增可选配置 `simulateTyping: boolean`
2. 当收到 `send(Reply, Context)` 时，若启用模拟，将完整文本按标点符号切分为多段
3. 通过 `client.push()` 逐段发送，段间 sleep 200-500ms

**约束**：
- 此功能与流式架构无关，仅是 UX 优化
- 受 openilink 速率限制影响，需谨慎开启

---

## 6. 关键代码骨架

### 6.1 VesselToolLoop.java

```java
package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.*;
import meta.claw.core.tool.SpiToolCall;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class VesselToolLoop {
    
    private static final int DEFAULT_MAX_STEPS = 500;
    
    private final String vesselId;
    private final String sessionId;
    private final String userMessage;
    private final LlmClientManager llmClientManager;
    private final ToolExecutor toolExecutor;
    private final ShortMemoryManager shortMemoryManager;
    private final PromptContextManager promptContextManager;
    private final SystemPromptBuilder systemPromptBuilder;
    private final SpiStreamingCallback callback;
    private final VesselExecutionMetrics metrics = new VesselExecutionMetrics();
    
    private int step = 0;
    private final List<SpiMessage> history = new ArrayList<>();
    
    public VesselToolLoop(String vesselId, String sessionId, String userMessage,
                          LlmClientManager llmClientManager,
                          ToolExecutor toolExecutor,
                          ShortMemoryManager shortMemoryManager,
                          PromptContextManager promptContextManager,
                          SystemPromptBuilder systemPromptBuilder,
                          SpiStreamingCallback callback) {
        this.vesselId = vesselId;
        this.sessionId = sessionId;
        this.userMessage = userMessage;
        this.llmClientManager = llmClientManager;
        this.toolExecutor = toolExecutor;
        this.shortMemoryManager = shortMemoryManager;
        this.promptContextManager = promptContextManager;
        this.systemPromptBuilder = systemPromptBuilder;
        this.callback = callback;
    }
    
    public void run() {
        callback.onStart();
        
        String systemPrompt = resolveSystemPrompt(vesselId);
        history.addAll(llmClientManager.buildLlmRequest(vesselId, sessionId, systemPrompt));
        history.add(SpiMessage.user(userMessage));
        
        try {
            while (step < DEFAULT_MAX_STEPS) {
                step++;
                metrics.incrementSteps();
                
                StringBuilder contentBuilder = new StringBuilder();
                AtomicReference<SpiChatResponse> responseRef = new AtomicReference<>();
                CountDownLatch latch = new CountDownLatch(1);
                
                SpiChatRequest request = SpiChatRequest.builder()
                    .vesselName(vesselId)
                    .messages(new ArrayList<>(history))
                    .build();
                
                llmClientManager.chatStream(request, new SpiStreamingCallback() {
                    @Override public void onStart() {}
                    @Override public void onChunk(String chunk) {
                        contentBuilder.append(chunk);
                        callback.onChunk(chunk);
                    }
                    @Override public void onToolCall(SpiToolCall toolCall) {}
                    @Override public void onComplete(SpiChatResponse resp) {
                        responseRef.set(resp);
                        latch.countDown();
                    }
                    @Override public void onError(Throwable error) {
                        latch.countDown();
                        callback.onError(error);
                    }
                });
                
                latch.await();
                
                SpiChatResponse response = responseRef.get();
                if (response == null) {
                    throw new IllegalStateException("No response at step " + step);
                }
                
                if (response.usage() != null) {
                    metrics.addUsage(response.usage());
                }
                
                history.add(SpiMessage.assistant(contentBuilder.toString()));
                
                if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                    metrics.addToolCalls(response.toolCalls().size());
                    List<ToolResult> results = toolExecutor.execute(response.toolCalls());
                    for (ToolResult result : results) {
                        history.add(SpiMessage.tool(result.getContent()));
                    }
                    continue;
                }
                
                callback.onComplete(response);
                break;
            }
        } catch (Exception e) {
            log.error("[VesselToolLoop] Error at step {}: {}", step, e.getMessage(), e);
            callback.onError(e);
        }
        
        shortMemoryManager.appendHistory(vesselId, sessionId, history);
    }
    
    private String resolveSystemPrompt(String vesselId) {
        try {
            PromptContext ctx = promptContextManager.create(vesselId);
            return systemPromptBuilder.build(ctx);
        } catch (Exception e) {
            log.warn("Failed to build system prompt for vessel {}: {}", vesselId, e.getMessage());
            return null;
        }
    }
}
```

### 6.2 VesselResponseChunkEvent.java

```java
package meta.claw.core.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import meta.claw.core.message.Context;

@Getter
@AllArgsConstructor
public class VesselResponseChunkEvent {
    private final String channelType;
    private final String chunk;
    private final String sessionId;
    private final Context context;
    private final boolean last;
    private final int step;
}
```

### 6.3 WebSocketChannel.java（精简骨架）

```java
package meta.claw.gateway.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.message.Context;
import meta.claw.core.message.Reply;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.Channel;
import meta.claw.gateway.channel.ChatMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class WebSocketChannel implements Channel {
    
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Sinks.Many<String>> chunkSinks = new ConcurrentHashMap<>();
    private final Gateway gateway;
    private final ObjectMapper objectMapper;
    
    public WebSocketChannel(Gateway gateway, ObjectMapper objectMapper) {
        this.gateway = gateway;
        this.objectMapper = objectMapper;
    }
    
    @Override public String getChannelType() { return "websocket"; }
    @Override public void startup() { log.info("[WebSocketChannel] started"); }
    @Override public void handleText(ChatMessage msg) {
        gateway.onInboundMessage(msg, getChannelType());
    }
    
    public void registerSession(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
        chunkSinks.put(sessionId, Sinks.many().unicast().onBackpressureBuffer());
    }
    
    public void unregisterSession(String sessionId) {
        sessions.remove(sessionId);
        Sinks.Many<String> sink = chunkSinks.remove(sessionId);
        if (sink != null) sink.tryEmitComplete();
    }
    
    @Override
    public void send(Reply reply, Context context) {
        // 非流式兜底
    }
    
    @Override
    public void sendChunk(String chunk, Context context) {
        emit(context.getSessionId(), "chunk", chunk);
    }
    
    @Override
    public void sendChunkEnd(Context context) {
        emit(context.getSessionId(), "end", "");
    }
    
    private void emit(String sessionId, String type, String content) {
        Sinks.Many<String> sink = chunkSinks.get(sessionId);
        if (sink == null) return;
        try {
            String payload = objectMapper.writeValueAsString(
                Map.of("type", type, "content", content));
            sink.tryEmitNext(payload);
        } catch (Exception e) {
            log.error("Emit failed: {}", e.getMessage());
        }
    }
    
    public Flux<String> getOutboundFlux(String sessionId) {
        return chunkSinks.computeIfAbsent(sessionId,
            k -> Sinks.many().unicast().onBackpressureBuffer()).asFlux();
    }
}
```

---

## 7. 风险与待决策项

1. **`LlmClientManager.chatStream()` 当前未解析 `toolCalls`**：
   - 现有 `chatStream()` 的 `onComplete` 返回的 `SpiChatResponse` 中 `toolCalls` 固定为 `null`（见 `LlmClientManager.java:170`）。
   - **决策**：需要在 `chatStream()` 中集成 Spring AI 的 tool calling 能力，或先通过 `chat()` 非流式方式获取 tool_calls，再决定下一步。推荐前者：在 `Flux` 消费结束后，检查 `ChatResponse` 中是否包含 `ToolResponseMessage`。

2. **`ToolExecutor` 接口尚未确认**：
   - 当前代码库中未找到 `ToolExecutor` 类，需要确认其位置或是否需要新建。
   - **决策**：若不存在，在 Phase 1 中新建 `ToolExecutor` 接口 + 至少一个 mock 实现用于单测。

3. **`ShortMemoryManager.appendHistory()` 签名**：
   - 当前 `ShortMemoryManager` 提供 `getHistory()`，需确认是否有批量 `append` 方法。
   - **决策**：若无，在 Phase 1 中新增 `appendHistory(String vesselId, String sessionKey, List<SpiMessage> messages)`。

4. **Reactive 线程模型**：
   - `WebSocketChannel.sendChunk()` 由 Guava EventBus 同步线程调用，向 Reactor `Sinks` 发射数据，需注意 `Sinks.Many` 的线程安全性。
   - **决策**：`Sinks.many().unicast().onBackpressureBuffer()` 是线程安全的，可直接使用。

5. **WebSocket 连接数与背压**：
   - 当前使用 `unicast` Sink，每个会话一个。高并发时需评估内存占用。
   - **决策**：P1 阶段不处理，P2 若压测出现问题可改为 `multicast` 或增加 TTL 清理。

---

## 附录：现有代码引用索引

| 文件 | 路径 | 在本设计中的角色 |
|------|------|----------------|
| `SpiStreamingCallback.java` | `meta-claw-core/src/main/java/meta/claw/core/llm/SpiStreamingCallback.java` | 流式回调 SPI，无需修改 |
| `LlmClientManager.java` | `meta-claw-core/src/main/java/meta/claw/core/runtime/LlmClientManager.java` | 需扩展 `toolCalls` 解析能力 |
| `VesselRuntime.java` | `meta-claw-core/src/main/java/meta/claw/core/runtime/VesselRuntime.java` | 新增 `chatStream()` |
| `AgentLoop.java` | `meta-claw-core/src/main/java/meta/claw/core/runtime/AgentLoop.java` | 新增流式分支 |
| `Channel.java` | `meta-claw-gateway/src/main/java/meta/claw/gateway/channel/Channel.java` | 新增默认方法 `sendChunk()` |
| `Gateway.java` | `meta-claw-gateway/src/main/java/meta/claw/gateway/Gateway.java` | 订阅 `VesselResponseChunkEvent` |
| `WeixinChannel.java` | `meta-claw-gateway-weixin/.../WeixinChannel.java` | 不实现 `sendChunk`，保持现状 |
| `expert_runtime.py` | `.rwa/expert_project/expert/runtime/expert_runtime.py` | 设计参考来源 |
