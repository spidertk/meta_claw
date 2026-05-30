package meta.claw.core.runtime;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiLlmClient;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.llm.provider.LlmClientProviderManager;
import meta.claw.core.memory.MemoryMessage;
import meta.claw.core.memory.MemoryMessageConverter;
import meta.claw.core.tool.registry.ToolRegistry;
import meta.claw.core.vessel.VesselConfigResolver;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.tool.SpiToolCall;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 基于 Spring AI ChatClient 的 SpiLlmClient 实现。
 */
@Slf4j
@Component
public class LlmClientManager implements SpiLlmClient {
    @Autowired
    private LlmClientProviderManager llmClientProviderManager;
    @Autowired
    private VesselConfigResolver vesselConfigResolver;

    @Autowired
    private ToolRegistry toolRegistry;

    private ChatClient buildChatClient(String vesselName) {
        ProviderConfig providerConfig = vesselConfigResolver.loadProviderConfig(vesselName);
        return llmClientProviderManager.create(providerConfig);
    }

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
                case "tool" -> restored.add(SpiMessage.tool(message.getContent()));
                default -> {
                    // System prompts are rebuilt from current vessel config when resuming.
                }
            }
        }
        return restored;
    }



    @Override
    public SpiChatResponse chat(SpiChatRequest request) {
        log.debug("LlmClientManager chat vessel={}, messages={}", request.getCtx().getVesselName(), request.getMessages().size());

        List<Message> messages = request.getMessages().stream()
                .map(this::toSpringMessage)
                .collect(Collectors.toCollection(ArrayList::new));

        List<Object> toolInstances = toolRegistry.getToolInstances();
        logRequestParams(messages, toolInstances);

        ChatResponse chatResponse = buildChatClient(request.getCtx().getVesselName())
                .prompt(new Prompt(messages))
                .tools(toolInstances.toArray())
                .call()
                .chatResponse();

        Generation gen = chatResponse.getResult();
        String content = gen != null && gen.getOutput() != null ? gen.getOutput().getText() : "";
        String reasoningContent = extractReasoningContent(gen);
        SpiUsage usage = extractUsage(chatResponse);

        return SpiChatResponse.builder()
                .content(content != null ? content : "")
                .reasoningContent(reasoningContent)
                .usage(usage)
                .build();
    }

    @Override
    public void chatStream(SpiChatRequest request, SpiStreamingCallback callback) {
        long startTime = System.currentTimeMillis();
        callback.onStart();

        List<Message> messages = request.getMessages().stream()
                .map(this::toSpringMessage)
                .collect(Collectors.toCollection(ArrayList::new));

        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        long[] firstChunkTime = {-1};
        int[] chunkCount = {0};
        long[] lastChunkTime = {startTime};
        AtomicReference<SpiUsage> usageRef = new AtomicReference<>();
        ObjectMapper objectMapper = new ObjectMapper();

        List<Object> toolInstances = toolRegistry.getToolInstances();
        logRequestParams(messages, toolInstances);

        try {
            buildChatClient(request.getCtx().getVesselName())
                    .prompt(new Prompt(messages))
                    .tools(toolInstances.toArray())
                    .advisors(spec -> spec
                            .param("vesselName", request.getCtx().getVesselName())
                            .param("sessionId", request.getSessionId())
                            .param("memoryConfig", request.getCtx().getMemoryConfig()))
                    .stream()
                    .chatResponse()
                    .doOnNext(response -> {
                        chunkCount[0]++;
                        long elapsed = System.currentTimeMillis() - startTime;
                        long gap = elapsed - lastChunkTime[0];
                        lastChunkTime[0] = elapsed;

                        if (firstChunkTime[0] == -1) {
                            firstChunkTime[0] = elapsed;
                        }

                        Generation gen = response.getResult();
                        if (gen != null && gen.getOutput() != null) {
                            String content = gen.getOutput().getText();
                            String reasoningChunk = extractReasoningContent(gen);

                            // 注意：Spring AI 1.1.7 将 reasoningContent 存入 AssistantMessage.metadata (properties map)

                            if (reasoningChunk != null && !reasoningChunk.isEmpty()) {
                                reasoningBuilder.append(reasoningChunk);
                                callback.onReasoningChunk(reasoningChunk);
                            }
                            if (content != null && !content.isEmpty()) {
                                contentBuilder.append(content);
                                callback.onChunk(content);
                            }

                            // 检测 tool calls
                            if (gen.getOutput() instanceof AssistantMessage am && am.hasToolCalls()) {
                                String finishReason = gen.getMetadata() != null ? gen.getMetadata().getFinishReason() : null;
                                if ("tool_calls".equals(finishReason)) {
                                    am.getToolCalls().forEach(tc -> {
                                        try {
                                            Map<String, Object> args = objectMapper.readValue(
                                                    tc.arguments(), new TypeReference<>() {});
                                            SpiToolCall spiToolCall = SpiToolCall.builder()
                                                    .id(tc.id())
                                                    .name(tc.name())
                                                    .arguments(args)
                                                    .build();
                                            callback.onToolCall(spiToolCall);
                                        } catch (Exception e) {
                                            log.warn("Failed to parse tool call arguments: {}", tc.arguments(), e);
                                        }
                                    });
                                }
                            }
                        }

                        // usage 通常在最后一个 chunk 中返回
                        SpiUsage usage = extractUsage(response);
                        if (usage != null) {
                            usageRef.set(usage);
                            callback.onUsage(usage);
                        }
                    })
                    .doOnError(error -> {
                        long totalTime = System.currentTimeMillis() - startTime;
                        if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException wex) {
                            log.error("[STREAM] HTTP Error after {}ms: status={}, body={}",
                                    totalTime, wex.getStatusCode(), wex.getResponseBodyAsString());
                        } else {
                            log.error("[STREAM] Error occurred after {}ms: {}", totalTime, error.getMessage(), error);
                        }
                        callback.onError(error);
                    })
                    .doOnComplete(() -> {
                        long totalTime = System.currentTimeMillis() - startTime;

                        SpiChatResponse spiResponse = SpiChatResponse.builder()
                                .content(contentBuilder.toString())
                                .reasoningContent(reasoningBuilder.toString())
                                .usage(usageRef.get())
                                .build();
                        callback.onComplete(spiResponse);
                    })
                    .blockLast();
        } catch (Exception e) {
            long totalTime = System.currentTimeMillis() - startTime;
            log.error("[STREAM] Exception after {}ms: {}", totalTime, e.getMessage(), e);
            callback.onError(e);
        }
    }

    @Override
    public CompletableFuture<SpiChatResponse> chatAsync(SpiChatRequest request) {
        return CompletableFuture.supplyAsync(() -> chat(request));
    }

    private static String extractReasoningContent(Generation gen) {
        if (gen == null) {
            return null;
        }
        // Spring AI 1.1.7 将 reasoningContent 存入 AssistantMessage.metadata (properties map)
        // 而非 ChatGenerationMetadata。需要同时检查两处。
        String reasoningContent = null;
        if (gen.getOutput() instanceof AssistantMessage am && am.getMetadata() != null) {
            Object rc = am.getMetadata().get("reasoningContent");
            if (rc instanceof String s && !s.isEmpty()) {
                reasoningContent = s;
            }
        }
        if (reasoningContent == null && gen.getMetadata() != null) {
            Object rc = gen.getMetadata().get("reasoningContent");
            if (rc instanceof String s && !s.isEmpty()) {
                reasoningContent = s;
            }
        }
        return reasoningContent;
    }

    private static SpiUsage extractUsage(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return null;
        }
        org.springframework.ai.chat.metadata.Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return null;
        }
        return SpiUsage.builder()
                .promptTokens(usage.getPromptTokens())
                .completionTokens(usage.getCompletionTokens())
                .totalTokens(usage.getTotalTokens())
                .build();
    }

    private void logRequestParams(List<Message> messages, List<Object> toolInstances) {
        if (!log.isDebugEnabled()) {
            return;
        }
        try {
            List<Map<String, Object>> msgList = new ArrayList<>();
            for (Message m : messages) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("role", m.getMessageType().getValue());
                map.put("content", m.getText());
                msgList.add(map);
            }
            String msgsJson = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(msgList);
            log.debug("[LLM-REQUEST] messages={}\n[LLM-REQUEST] tools count={}", msgsJson, toolInstances.size());
        } catch (Exception e) {
            log.error("[LLM-REQUEST] messages count={}, tools count={}, err={}", messages.size(), toolInstances.size(), e.getMessage(), e);
        }
    }

    private Message toSpringMessage(SpiMessage msg) {
        return switch (msg.getRole()) {
            case "system" -> new SystemMessage(msg.getContent());
            case "user" -> new UserMessage(msg.getContent());
            case "assistant" -> new AssistantMessage(msg.getContent());
            case "tool" -> ToolResponseMessage.builder()
                    .responses(List.of(
                            new ToolResponseMessage.ToolResponse("tool", "tool", msg.getContent())
                    ))
                    .build();
            default -> {
                log.warn("Unknown message role '{}', defaulting to user message", msg.getRole());
                yield new UserMessage(msg.getContent());
            }
        };
    }
}
