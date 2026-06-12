package meta.claw.core.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.runtime.subsystem.ToolSubSystem;
import meta.claw.core.tool.SpiToolCall;
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 执行任务，支持多轮 tool-call。
     */
    public Reply execute(TaskContext ctx, SpiChatRequest request) {
        ToolSubSystem toolSub = ctx.getSubSystem("tool");
        List<ToolCallback> tools = toolSub != null ? toolSub.getToolCallbacks() : List.of();
        Map<String, ToolCallback> toolMap = tools.stream()
                .collect(Collectors.toMap(
                        tc -> tc.getToolDefinition().name(),
                        Function.identity(),
                        (a, b) -> a));

        List<SpiMessage> messages = new ArrayList<>(request.getMessages());

        for (int step = 1; step <= maxSteps; step++) {
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

            if (response == null || response.toolCalls() == null || response.toolCalls().isEmpty()) {
                String content = response != null ? response.content() : "";
                return new Reply(ReplyType.TEXT, content);
            }

            // 添加 assistant 消息（含 tool calls）
            messages.add(SpiMessage.assistant(response.content(), response.toolCalls()));
            ctx.getMessages().add(SpiMessage.assistant(response.content(), response.toolCalls()));

            // 执行 tool calls 并将结果回注到消息列表
            for (SpiToolCall tc : response.toolCalls()) {
                ToolCallback callback = toolMap.get(tc.getName());
                String result;
                if (callback != null) {
                    try {
                        String argsJson = objectMapper.writeValueAsString(tc.getArguments());
                        result = callback.call(argsJson);
                        log.debug("Tool {} executed, result: {}", tc.getName(), result);
                    } catch (Exception e) {
                        log.warn("Tool {} execution failed: {}", tc.getName(), e.getMessage(), e);
                        result = "Error: " + e.getMessage();
                    }
                } else {
                    log.warn("Tool {} not found in registry", tc.getName());
                    result = "Error: tool not found";
                }

                String toolResultJson;
                try {
                    toolResultJson = objectMapper.writeValueAsString(Map.of(
                            "toolCallId", tc.getId(),
                            "toolName", tc.getName(),
                            "result", result
                    ));
                } catch (Exception e) {
                    toolResultJson = "{\"toolCallId\":\"" + tc.getId() + "\",\"result\":\"" + result.replace("\"", "\\\"") + "\"}";
                }
                messages.add(SpiMessage.tool(toolResultJson));
                ctx.getMessages().add(SpiMessage.tool(toolResultJson));
            }
        }

        throw new RuntimeException("超过最大步数: " + maxSteps);
    }
}
