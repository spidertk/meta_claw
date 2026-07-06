package meta.claw.core.llm.advisor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import meta.claw.core.tool.SpiToolCall;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流式 LLM 调用的响应提取与指标记录 Advisor。
 * <p>
 * 在流式响应过程中累积 content、reasoningContent、usage 与 tool calls（处理分段 arguments），
 * 流结束时将结果写入 {@link TaskContext.LlmCallContext}，并触发 callback.onToolCall() 与指标记录。
 * </p>
 */
@Slf4j
public class MetaClawResponseStreamAdvisor implements StreamAdvisor {

    private final MetricsRecorder metricsRecorder;
    private final ObjectMapper objectMapper;

    public MetaClawResponseStreamAdvisor(MetricsRecorder metricsRecorder, ObjectMapper objectMapper) {
        this.metricsRecorder = metricsRecorder;
        this.objectMapper = objectMapper;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        TaskContext.LlmCallContext ctx = (TaskContext.LlmCallContext) request.context().get(TaskContext.LlmCallContext.CONTEXT_KEY);

        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        SpiUsage[] usageHolder = new SpiUsage[1];
        Map<String, AssistantMessage.ToolCall> accumulatingToolCalls = new LinkedHashMap<>();
        Map<String, StringBuilder> accumulatingToolArgs = new LinkedHashMap<>();

        return chain.nextStream(request)
                .doOnNext(response -> {
                    ChatResponse chatResponse = response != null ? response.chatResponse() : null;
                    if (chatResponse == null || chatResponse.getResult() == null) {
                        return;
                    }
                    Generation gen = chatResponse.getResult();
                    AssistantMessage am = gen.getOutput() instanceof AssistantMessage
                            ? (AssistantMessage) gen.getOutput() : null;

                    if (am != null) {
                        if (am.getText() != null) {
                            contentBuilder.append(am.getText());
                            notifyChunk(ctx, am.getText());
                        }
                        String reasoning = extractReasoningContent(am);
                        if (reasoning != null) {
                            reasoningBuilder.append(reasoning);
                            notifyReasoningChunk(ctx, reasoning);
                        }
                        if (am.hasToolCalls()) {
                            accumulateToolCalls(am, accumulatingToolCalls, accumulatingToolArgs);
                        }
                    }

                    SpiUsage usage = extractUsage(chatResponse);
                    if (usage != null) {
                        usageHolder[0] = usage;
                    }

                    String finishReason = gen.getMetadata() != null ? gen.getMetadata().getFinishReason() : null;
                    boolean isToolCallFinish = finishReason != null
                            && (finishReason.equalsIgnoreCase("tool_calls") || finishReason.equalsIgnoreCase("tool_call"));
                    if (isToolCallFinish && !accumulatingToolCalls.isEmpty()) {
                        List<SpiToolCall> parsed = parseAccumulatedToolCalls(accumulatingToolCalls, accumulatingToolArgs);
                        notifyToolCalls(ctx, parsed);
                    }
                })
                .doOnComplete(() -> {
                    if (ctx == null) {
                        return;
                    }
                    // 流正常结束时，若还有未通知的累积 tool calls（如 finishReason 未触发），统一解析
                    List<SpiToolCall> parsed = parseAccumulatedToolCalls(accumulatingToolCalls, accumulatingToolArgs);
                    if (!parsed.isEmpty()) {
                        notifyToolCalls(ctx, parsed);
                    }

                    long latency = System.currentTimeMillis() - ctx.getStartTime();
                    ctx.setContent(contentBuilder.toString());
                    ctx.setReasoningContent(reasoningBuilder.toString());
                    ctx.setUsage(usageHolder[0]);
                    ctx.setToolCalls(parsed);
                    ctx.recordLlmMetrics(metricsRecorder, latency);

                    log.debug("[MetaClawResponseStreamAdvisor] vessel={}, latency={}ms, contentLen={}, reasoningLen={}, toolCalls={}",
                            ctx.getVesselId(), latency, contentBuilder.length(), reasoningBuilder.length(), parsed.size());
                });
    }

    @Override
    public String getName() {
        return "MetaClawResponseStreamAdvisor";
    }

    @Override
    public int getOrder() {
        // 最内侧，紧挨模型调用
        return 100;
    }

    private String extractReasoningContent(AssistantMessage am) {
        if (am == null || am.getMetadata() == null) {
            return null;
        }
        Object rc = am.getMetadata().get("reasoningContent");
        if (rc instanceof String s && !s.isEmpty()) {
            return s;
        }
        return null;
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

    private static void accumulateToolCalls(AssistantMessage am,
                                            Map<String, AssistantMessage.ToolCall> accumulatingToolCalls,
                                            Map<String, StringBuilder> accumulatingToolArgs) {
        for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
            String id = tc.id();
            if (id == null && accumulatingToolCalls.size() == 1) {
                id = accumulatingToolCalls.keySet().iterator().next();
            }
            if (id == null) {
                log.warn("Ignoring tool call chunk without id: {}", tc);
                continue;
            }
            AssistantMessage.ToolCall existing = accumulatingToolCalls.get(id);
            String name = tc.name() != null ? tc.name() : (existing != null ? existing.name() : null);
            String type = tc.type() != null ? tc.type() : (existing != null ? existing.type() : "function");
            String args = tc.arguments() != null ? tc.arguments() : "";
            accumulatingToolCalls.put(id, new AssistantMessage.ToolCall(id, type, name, args));
            accumulatingToolArgs.computeIfAbsent(id, k -> new StringBuilder()).append(args);
        }
    }

    private List<SpiToolCall> parseAccumulatedToolCalls(Map<String, AssistantMessage.ToolCall> accumulatingToolCalls,
                                                        Map<String, StringBuilder> accumulatingToolArgs) {
        List<SpiToolCall> result = new ArrayList<>();
        for (AssistantMessage.ToolCall tc : accumulatingToolCalls.values()) {
            StringBuilder argsBuilder = accumulatingToolArgs.get(tc.id());
            String fullArgs = argsBuilder != null ? argsBuilder.toString() : tc.arguments();
            try {
                Map<String, Object> args = objectMapper.readValue(fullArgs, new TypeReference<>() {});
                result.add(SpiToolCall.builder().id(tc.id()).name(tc.name()).arguments(args).build());
            } catch (Exception e) {
                log.warn("Failed to parse tool call arguments: {}", fullArgs, e);
            }
        }
        return result;
    }

    private void notifyToolCalls(TaskContext.LlmCallContext ctx, List<SpiToolCall> toolCalls) {
        if (ctx == null || ctx.getStreamingCallback() == null || toolCalls.isEmpty()) {
            return;
        }
        for (SpiToolCall toolCall : toolCalls) {
            ctx.getStreamingCallback().onToolCall(toolCall);
        }
    }

    private void notifyChunk(TaskContext.LlmCallContext ctx, String chunk) {
        if (ctx == null || ctx.getStreamingCallback() == null || chunk == null || chunk.isEmpty()) {
            return;
        }
        ctx.getStreamingCallback().onChunk(chunk);
    }

    private void notifyReasoningChunk(TaskContext.LlmCallContext ctx, String chunk) {
        if (ctx == null || ctx.getStreamingCallback() == null || chunk == null || chunk.isEmpty()) {
            return;
        }
        ctx.getStreamingCallback().onReasoningChunk(chunk);
    }
}
