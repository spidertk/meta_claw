package meta.claw.core.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiMessage;
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
 * Agent 执行引擎。
 * <p>基于 ReAct 模式实现多轮 tool-call 循环：LLM 思考 → 工具调用 → 结果回注 → 继续思考。</p>
 */
@Slf4j
@Component
public class AgentExecutor {

    @Value("${vessel.agent.max-steps:50}")
    private int maxSteps;

    @Autowired
    private LlmClientManager llmClient;

    @Autowired(required = false)
    private MetricsRecorder metricsRecorder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行任务，支持多轮 tool-call。
     */
    public Reply execute(TaskContext ctx, SpiChatRequest request) {
        ToolSubSystem toolSub = ctx.getSubSystem("tool");
        HitlSubSystem hitlSub = ctx.getSubSystem("hitl");
        List<ToolCallback> tools = toolSub != null ? toolSub.getToolCallbacks() : List.of();
        Map<String, ToolCallback> toolMap = buildToolMap(tools);

        List<SpiMessage> messages = new ArrayList<>(request.getMessages());

        return reactLoop(ctx, request, messages, tools, toolMap, hitlSub, 1);
    }

    /**
     * 从 HITL 挂起状态恢复，继续完成 ReAct 循环。
     *
     * @param request 已包含挂起前 assistant tool_calls 消息的完整请求
     * @param ticket  挂起时生成的审批票证
     * @param resolution 用户决议
     */
    public Reply resume(TaskContext ctx, SpiChatRequest request,
                        ApprovalTicket ticket, ApprovalResolution resolution) {
        ToolSubSystem toolSub = ctx.getSubSystem("tool");
        List<ToolCallback> tools = toolSub != null ? toolSub.getToolCallbacks() : List.of();
        Map<String, ToolCallback> toolMap = buildToolMap(tools);

        List<SpiMessage> messages = new ArrayList<>(request.getMessages());

        // 根据决议执行被挂起的 tool calls
        for (ApprovalItem item : ticket.getItems()) {
            ApprovalStatus status = resolution.getDecisions().get(item.getToolCallId());
            String result = (status == ApprovalStatus.APPROVED)
                    ? executeToolCall(toolMap.get(item.getToolName()), item)
                    : "REJECTED by operator";
            recordToolCall(ctx, item.getToolName());

            messages.add(SpiMessage.tool(result, item.getToolCallId(), item.getToolName()));
            ctx.getMessages().add(SpiMessage.tool(result, item.getToolCallId(), item.getToolName()));
        }

        // 继续 ReAct 循环（从第 2 步开始，因为第 1 步已生成 tool_calls）
        return reactLoop(ctx, request, messages, tools, toolMap, null, 2);
    }

    private Reply reactLoop(TaskContext ctx, SpiChatRequest request, List<SpiMessage> messages,
                            List<ToolCallback> tools, Map<String, ToolCallback> toolMap,
                            HitlSubSystem hitlSub, int startStep) {
        for (int step = startStep; step <= maxSteps; step++) {
            ctx.getSteps().add(StepRecord.builder()
                    .stepNumber(step)
                    .action("llm-call")
                    .description("Calling LLM at step " + step)
                    .build());

            SpiChatResponse response = llmClient.chatWithTools(
                    SpiChatRequest.builder()
                            .vesselId(request.getVesselId())
                            .sessionId(request.getSessionId())
                            .messages(messages)
                            .build(),
                    tools.toArray(new ToolCallback[0])
            );
            ctx.addTokenUsage(response != null ? response.usage() : null);

            if (response == null || response.toolCalls() == null || response.toolCalls().isEmpty()) {
                String content = response != null ? response.content() : "";
                return new Reply(ReplyType.TEXT, content);
            }

            // HITL 检查：需要审批时挂起执行
            if (hitlSub != null) {
                HitlEvaluation evaluation = hitlSub.evaluate(response.toolCalls(), ctx);
                if (evaluation.hasSuspensions()) {
                    throw new HitlSuspendedException(evaluation.getTicket());
                }
            }

            // 添加 assistant 消息（含 reasoning + tool calls）
            messages.add(SpiMessage.assistant(response.content(), response.reasoningContent(), response.toolCalls()));
            ctx.getMessages().add(SpiMessage.assistant(response.content(), response.reasoningContent(), response.toolCalls()));

            // 执行 tool calls 并将结果回注到消息列表
            for (SpiToolCall tc : response.toolCalls()) {
                String result = executeToolCall(toolMap.get(tc.getName()), tc);
                recordToolCall(ctx, tc.getName());
                messages.add(SpiMessage.tool(result, tc.getId(), tc.getName()));
                ctx.getMessages().add(SpiMessage.tool(result, tc.getId(), tc.getName()));
            }
        }

        throw new RuntimeException("超过最大步数: " + maxSteps);
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

}
