package meta.claw.core.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.runtime.hitl.*;
import meta.claw.core.runtime.subsystem.HitlSubSystem;
import meta.claw.core.runtime.subsystem.ToolSubSystem;
import meta.claw.core.tool.SpiToolCall;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流式 Agent 执行引擎。
 * <p>基于 ReAct 模式实现多轮 tool-call 循环，content/reasoning 通过 {@link SpiStreamingCallback} 实时输出，
 * 并在 tool-call 执行点集成 HITL 审批。</p>
 */
@Slf4j
@Component
public class StreamingAgentExecutor {

    @Value("${vessel.agent.max-steps:50}")
    private int maxSteps;

    @Autowired
    private LlmClientManager llmClient;

    @Autowired(required = false)
    private MetricsRecorder metricsRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行流式任务，支持多轮 tool-call 与 HITL 审批。
     */
    public Reply execute(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback) {
        ToolSubSystem toolSub = ctx.getSubSystem("tool");
        HitlSubSystem hitlSub = ctx.getSubSystem("hitl");
        List<ToolCallback> tools = toolSub != null ? toolSub.getToolCallbacks() : List.of();
        Map<String, ToolCallback> toolMap = buildToolMap(tools);

        List<SpiMessage> messages = new ArrayList<>(request.getMessages());

        for (int step = 1; step <= maxSteps; step++) {
            ctx.getSteps().add(StepRecord.builder()
                    .stepNumber(step)
                    .action("llm-call-stream")
                    .description("Calling LLM stream at step " + step)
                    .build());

            // 使用 callback 透传 content/reasoning，但由本类控制 tool-call 循环
            AccumulatingCallback accumulatingCallback = new AccumulatingCallback(callback);
            SpiChatResponse response = llmClient.streamWithTools(
                    SpiChatRequest.builder()
                            .vesselId(request.getVesselId())
                            .sessionId(request.getSessionId())
                            .messages(messages)
                            .build(),
                    tools.toArray(new ToolCallback[0]),
                    accumulatingCallback
            );
            ctx.addTokenUsage(response != null ? response.usage() : null);

            if (response == null || response.toolCalls() == null || response.toolCalls().isEmpty()) {
                String content = response != null ? response.content() : "";
                return new Reply(ReplyType.TEXT, content);
            }

            // HITL 检查
            if (hitlSub != null) {
                HitlEvaluation evaluation = hitlSub.evaluate(response.toolCalls(), ctx);
                if (evaluation.hasSuspensions()) {
                    ApprovalResolution resolution = callback.onHitlSuspend(evaluation.getTicket());
                    if (resolution == null) {
                        // 用户未给出决议，视为全部拒绝
                        resolution = ApprovalResolution.builder()
                                .ticketId(evaluation.getTicket().getTicketId())
                                .decisions(evaluation.getTicket().getItems().stream()
                                        .collect(Collectors.toMap(ApprovalItem::getToolCallId, item -> ApprovalStatus.REJECTED)))
                                .operator("stream-user")
                                .build();
                    }
                    executeApprovedToolCalls(messages, ctx, evaluation.getTicket(), resolution, toolMap);
                    continue;
                }
            }

            // 添加 assistant 消息（含 tool calls）
            messages.add(SpiMessage.assistant(response.content(), response.toolCalls()));
            ctx.getMessages().add(SpiMessage.assistant(response.content(), response.toolCalls()));

            // 执行 tool calls 并将结果回注到消息列表
            for (SpiToolCall tc : response.toolCalls()) {
                String result = executeToolCall(toolMap.get(tc.getName()), tc);
                recordToolCall(ctx, tc.getName());
                String toolResultJson = buildToolResultJson(tc.getId(), tc.getName(), result);
                messages.add(SpiMessage.tool(toolResultJson));
                ctx.getMessages().add(SpiMessage.tool(toolResultJson));
            }
        }

        throw new RuntimeException("超过最大步数: " + maxSteps);
    }

    private void executeApprovedToolCalls(List<SpiMessage> messages, TaskContext ctx,
                                          ApprovalTicket ticket, ApprovalResolution resolution,
                                          Map<String, ToolCallback> toolMap) {
        // 添加 assistant 消息（含 tool calls），从 ticket 重建
        List<SpiToolCall> toolCalls = ticket.getItems().stream()
                .map(item -> SpiToolCall.builder()
                        .id(item.getToolCallId())
                        .name(item.getToolName())
                        .arguments(parseArguments(item.getArgumentsJson()))
                        .build())
                .toList();
        messages.add(SpiMessage.assistant(null, toolCalls));
        ctx.getMessages().add(SpiMessage.assistant(null, toolCalls));

        for (ApprovalItem item : ticket.getItems()) {
            ApprovalStatus status = resolution.getDecisions().get(item.getToolCallId());
            String result = (status == ApprovalStatus.APPROVED)
                    ? executeToolCall(toolMap.get(item.getToolName()), item)
                    : "REJECTED by operator";
            recordToolCall(ctx, item.getToolName());
            String toolResultJson = buildToolResultJson(item.getToolCallId(), item.getToolName(), result);
            messages.add(SpiMessage.tool(toolResultJson));
            ctx.getMessages().add(SpiMessage.tool(toolResultJson));
        }
    }

    private Map<String, ToolCallback> buildToolMap(List<ToolCallback> tools) {
        return tools.stream()
                .collect(Collectors.toMap(
                        tc -> tc.getToolDefinition().name(),
                        Function.identity(),
                        (a, b) -> a));
    }

    private String executeToolCall(ToolCallback callback, SpiToolCall tc) {
        if (callback == null) {
            log.warn("Tool {} not found in registry", tc.getName());
            return "Error: tool not found";
        }
        try {
            String argsJson = objectMapper.writeValueAsString(tc.getArguments());
            String result = callback.call(argsJson);
            log.debug("Tool {} executed, result: {}", tc.getName(), result);
            return result;
        } catch (Exception e) {
            log.warn("Tool {} execution failed: {}", tc.getName(), e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    private String executeToolCall(ToolCallback callback, ApprovalItem item) {
        if (callback == null) {
            log.warn("Tool {} not found in registry", item.getToolName());
            return "Error: tool not found";
        }
        try {
            String result = callback.call(item.getArgumentsJson());
            log.debug("Tool {} executed, result: {}", item.getToolName(), result);
            return result;
        } catch (Exception e) {
            log.warn("Tool {} execution failed: {}", item.getToolName(), e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
    }

    private void recordToolCall(TaskContext ctx, String toolName) {
        ctx.incrementToolCallCount();
        if (metricsRecorder != null) {
            metricsRecorder.recordToolCall(ctx.getTask().getVesselId(), toolName);
        }
    }

    private String buildToolResultJson(String toolCallId, String toolName, String result) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "toolCallId", toolCallId,
                    "toolName", toolName,
                    "result", result
            ));
        } catch (Exception e) {
            return "{\"toolCallId\":\"" + toolCallId + "\",\"toolName\":\"" + toolName
                    + "\",\"result\":\"" + result.replace("\"", "\\\"") + "\"}";
        }
    }

    private Map<String, Object> parseArguments(String argumentsJson) {
        try {
            return objectMapper.readValue(argumentsJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments JSON: {}", argumentsJson, e);
            return Map.of();
        }
    }

    /**
     * 透传 callback，同时收集完整响应内容。
     */
    private static class AccumulatingCallback implements SpiStreamingCallback {
        private final SpiStreamingCallback delegate;
        private final StringBuilder contentBuilder = new StringBuilder();
        private final StringBuilder reasoningBuilder = new StringBuilder();

        AccumulatingCallback(SpiStreamingCallback delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onStart() {
            delegate.onStart();
        }

        @Override
        public void onChunk(String chunk) {
            contentBuilder.append(chunk);
            delegate.onChunk(chunk);
        }

        @Override
        public void onReasoningChunk(String chunk) {
            reasoningBuilder.append(chunk);
            delegate.onReasoningChunk(chunk);
        }

        @Override
        public void onToolCall(SpiToolCall toolCall) {
            delegate.onToolCall(toolCall);
        }

        @Override
        public ApprovalResolution onHitlSuspend(ApprovalTicket ticket) {
            return delegate.onHitlSuspend(ticket);
        }

        @Override
        public void onUsage(SpiUsage usage) {
            delegate.onUsage(usage);
        }

        @Override
        public void onComplete(SpiChatResponse response) {
            delegate.onComplete(response);
        }

        @Override
        public void onError(Throwable error) {
            delegate.onError(error);
        }
    }
}
