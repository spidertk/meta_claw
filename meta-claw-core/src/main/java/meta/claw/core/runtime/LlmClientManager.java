package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiLlmClient;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.llm.advisor.MetaClawCallContext;
import meta.claw.core.llm.provider.LlmClientProviderManager;
import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.MemoryMessageConverter;
import meta.claw.core.config.resolver.RuntimeConfigResolver;
import meta.claw.core.tool.SpiToolCall;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 基于 Spring AI ChatClient 的 SpiLlmClient 实现。
 * <p>
 * 作为纯适配器：将 {@link SpiMessage} 转换为 Spring AI {@link Message}，通过
 * {@link LlmClientProviderManager} 获取带公共 Advisor 栈的 {@link ChatClient}，
 * 最后从 {@link MetaClawCallContext} 读取 Advisors 写入的结果并组装 {@link SpiChatResponse}。
 * </p>
 */
@Slf4j
@Component
public class LlmClientManager implements SpiLlmClient {

    @Autowired
    private LlmClientProviderManager llmClientProviderManager;
    @Autowired
    private RuntimeConfigResolver runtimeConfigResolver;

    public List<SpiMessage> toSpiMessages(List<MemoryMessage> entries) {
        List<SpiMessage> restored = new ArrayList<>();
        for (MemoryMessage entry : entries) {
            SpiMessage message = MemoryMessageConverter.toSpiMessage(entry);
            if (message.getRole() == null) {
                continue;
            }
            switch (message.getRole().toLowerCase()) {
                case "user" -> restored.add(SpiMessage.user(message.getContent()));
                case "assistant" -> restored.add(
                        SpiMessage.assistant(message.getContent(), message.getReasoningContent(), message.getToolCalls()));
                case "tool" -> restored.add(
                        SpiMessage.tool(message.getContent(), message.getToolCallId(), message.getToolName()));
                default -> {
                    // System prompts are rebuilt from current vessel config when resuming.
                }
            }
        }
        return restored;
    }

    @Override
    public SpiChatResponse chat(SpiChatRequest request) {
        return chat(request, null);
    }

    /**
     * 同步 chat，支持与 {@link TaskContext} 绑定以复用上下文。
     */
    public SpiChatResponse chat(SpiChatRequest request, TaskContext taskContext) {
        log.debug("LlmClientManager chat vessel={}, messages={}", request.getVesselId(), request.getMessages().size());

        List<Message> messages = request.getMessages().stream()
                .map(this::toSpringMessage)
                .collect(Collectors.toCollection(ArrayList::new));

        MetaClawCallContext ctx = createCallContext(request, taskContext);

        buildChatClient(request.getVesselId())
                .prompt(new Prompt(messages))
                .advisors(spec -> spec.param(MetaClawCallContext.CONTEXT_KEY, ctx))
                .call()
                .chatResponse();

        return buildResponse(ctx);
    }

    /**
     * 单次 tool-aware 调用，返回的 SpiChatResponse 可能携带 toolCalls。
     * 由 AgentExecutor 手动控制 tool-call 循环。
     */
    public SpiChatResponse chatWithTools(SpiChatRequest request, TaskContext taskContext, ToolCallback... toolCallbacks) {
        log.debug("LlmClientManager chatWithTools vessel={}, messages={}", request.getVesselId(), request.getMessages().size());

        List<Message> messages = request.getMessages().stream()
                .map(this::toSpringMessage)
                .collect(Collectors.toCollection(ArrayList::new));

        MetaClawCallContext ctx = createCallContext(request, taskContext);

        buildChatClient(request.getVesselId())
                .prompt(new Prompt(messages))
                .advisors(spec -> spec
                        .param(MetaClawCallContext.CONTEXT_KEY, ctx)
                        .param(MetaClawCallContext.EXPLICIT_TOOL_CALLBACKS_KEY, toolCallbacks))
                .call()
                .chatResponse();

        return buildResponse(ctx);
    }

    /**
     * 单次 tool-aware 流式调用，返回最终响应（含 toolCalls），不执行 tool-call 循环。
     * <p>内容 chunk 通过 callback 实时输出，tool-call 累积由 {@link meta.claw.core.llm.advisor.MetaClawResponseStreamAdvisor} 处理。</p>
     */
    public SpiChatResponse streamWithTools(SpiChatRequest request, TaskContext taskContext,
                                           ToolCallback[] toolCallbacks, SpiStreamingCallback callback) {
        log.debug("LlmClientManager streamWithTools vessel={}, messages={}", request.getVesselId(), request.getMessages().size());

        List<Message> messages = request.getMessages().stream()
                .map(this::toSpringMessage)
                .collect(Collectors.toCollection(ArrayList::new));

        MetaClawCallContext ctx = createCallContext(request, taskContext);
        ctx.setStreamingCallback(callback);

        callback.onStart();
        try {
            buildChatClient(request.getVesselId())
                    .prompt(new Prompt(messages))
                    .advisors(spec -> spec
                            .param(MetaClawCallContext.CONTEXT_KEY, ctx)
                            .param(MetaClawCallContext.EXPLICIT_TOOL_CALLBACKS_KEY, toolCallbacks))
                    .stream()
                    .chatResponse()
                    .doOnNext(response -> {
                        // callback.onChunk/onReasoningChunk 由 streaming advisor 触发
                    })
                    .doOnError(error -> {
                        log.error("[STREAM-WITH-TOOLS] Error: {}", error.getMessage(), error);
                        callback.onError(error);
                    })
                    .doOnComplete(() -> {
                        SpiChatResponse response = buildResponse(ctx);
                        callback.onComplete(response);
                    })
                    .blockLast();
        } catch (Exception e) {
            log.error("[STREAM-WITH-TOOLS] Exception: {}", e.getMessage(), e);
            callback.onError(e);
            throw new RuntimeException("Stream with tools failed", e);
        }

        return buildResponse(ctx);
    }

    @Override
    public void chatStream(SpiChatRequest request, SpiStreamingCallback callback) {
        chatStream(request, null, callback);
    }

    /**
     * 流式 chat，复用 streamWithTools 的流式逻辑，无工具场景传空数组。
     */
    public void chatStream(SpiChatRequest request, TaskContext taskContext, SpiStreamingCallback callback) {
        streamWithTools(request, taskContext, new ToolCallback[0], callback);
    }

    @Override
    public CompletableFuture<SpiChatResponse> chatAsync(SpiChatRequest request) {
        return CompletableFuture.supplyAsync(() -> chat(request));
    }

    private MetaClawCallContext createCallContext(SpiChatRequest request, TaskContext taskContext) {
        if (taskContext != null) {
            return new MetaClawCallContext(taskContext);
        }
        return new MetaClawCallContext(request.getVesselId(), request.getSessionId());
    }

    private SpiChatResponse buildResponse(MetaClawCallContext ctx) {
        return SpiChatResponse.builder()
                .content(ctx.getContentOrEmpty())
                .reasoningContent(ctx.getReasoningContent())
                .toolCalls(ctx.getToolCallsOrEmpty())
                .usage(ctx.getUsage())
                .build();
    }

    private ChatClient buildChatClient(String vesselId) {
        ProviderConfig providerConfig = runtimeConfigResolver.resolve(vesselId).getProviderConfig();
        return llmClientProviderManager.create(providerConfig);
    }

    private Message toSpringMessage(SpiMessage msg) {
        return switch (msg.getRole()) {
            case "system" -> new SystemMessage(msg.getContent());
            case "user" -> new UserMessage(msg.getContent());
            case "assistant" -> {
                java.util.Map<String, Object> properties = new java.util.HashMap<>();
                if (msg.getReasoningContent() != null && !msg.getReasoningContent().isEmpty()) {
                    properties.put("reasoningContent", msg.getReasoningContent());
                    if (log.isDebugEnabled()) {
                        log.debug("[toSpringMessage] assistant message has reasoningContent, length={}", msg.getReasoningContent().length());
                    }
                }
                yield AssistantMessage.builder()
                        .content(msg.getContent() != null ? msg.getContent() : "")
                        .properties(properties)
                        .toolCalls(toSpringToolCalls(msg.getToolCalls()))
                        .build();
            }
            case "tool" -> {
                String toolCallId = msg.getToolCallId();
                String toolName = msg.getToolName();
                String content = msg.getContent();
                if (toolCallId == null || toolName == null) {
                    LegacyToolResult legacy = parseLegacyToolResultJson(content);
                    toolCallId = legacy.toolCallId;
                    toolName = legacy.toolName;
                    content = legacy.result;
                }
                yield ToolResponseMessage.builder()
                        .responses(List.of(
                                new ToolResponseMessage.ToolResponse(
                                        toolCallId != null ? toolCallId : "tool",
                                        toolName != null ? toolName : "tool",
                                        content)
                        ))
                        .build();
            }
            default -> {
                log.warn("Unknown message role '{}', defaulting to user message", msg.getRole());
                yield new UserMessage(msg.getContent());
            }
        };
    }

    private static List<AssistantMessage.ToolCall> toSpringToolCalls(List<SpiToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        return toolCalls.stream()
                .map(tc -> new AssistantMessage.ToolCall(
                        tc.getId(),
                        "function",
                        tc.getName(),
                        toArgumentsJson(mapper, tc.getArguments())))
                .collect(Collectors.toList());
    }

    private static String toArgumentsJson(com.fasterxml.jackson.databind.ObjectMapper mapper, Map<String, Object> arguments) {
        if (arguments == null) {
            return "{}";
        }
        try {
            return mapper.writeValueAsString(arguments);
        } catch (Exception e) {
            return "{}";
        }
    }

    private record LegacyToolResult(String toolCallId, String toolName, String result) {}

    private static LegacyToolResult parseLegacyToolResultJson(String json) {
        if (json == null || json.isBlank()) {
            return new LegacyToolResult(null, null, "");
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> map = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            return new LegacyToolResult(
                    String.valueOf(map.getOrDefault("toolCallId", "tool")),
                    String.valueOf(map.getOrDefault("toolName", "tool")),
                    String.valueOf(map.getOrDefault("result", json)));
        } catch (Exception e) {
            return new LegacyToolResult(null, null, json);
        }
    }
}
