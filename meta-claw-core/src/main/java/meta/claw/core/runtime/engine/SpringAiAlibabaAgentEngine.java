package meta.claw.core.runtime.engine;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.llm.SpiStreamingCallback;
import meta.claw.core.message.Reply;
import meta.claw.core.message.ReplyType;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.ApprovalItem;
import meta.claw.core.runtime.hitl.ApprovalResolution;
import meta.claw.core.runtime.hitl.ApprovalStatus;
import meta.claw.core.runtime.hitl.ApprovalTicket;
import meta.claw.core.runtime.subsystem.ToolSubSystem;
import meta.claw.core.tool.SpiToolCall;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Spring AI Alibaba {@link ReactAgent} 的执行引擎实现。
 *
 * <p>Phase 2 实现同步 call；Phase 3 接入 streamMessages；Phase 4 接入 HITL Hook。</p>
 */
@Component
public class SpringAiAlibabaAgentEngine implements AgentEngine {

    @Autowired
    private ReactAgentFactory reactAgentFactory;

    @Override
    public Reply execute(TaskContext ctx, SpiChatRequest request) {
        ReactAgent agent = reactAgentFactory.get(ctx);
        List<Message> messages = SpiMessageConverter.toSpringMessages(request.getMessages());
        try {
            AssistantMessage result = agent.call(messages);
            return new Reply(ReplyType.TEXT, result.getText());
        } catch (com.alibaba.cloud.ai.graph.exception.GraphRunnerException e) {
            throw new RuntimeException("Alibaba agent execution failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Reply executeStream(TaskContext ctx, SpiChatRequest request, SpiStreamingCallback callback) {
        ReactAgent agent = reactAgentFactory.get(ctx);
        List<Message> messages = SpiMessageConverter.toSpringMessages(request.getMessages());

        callback.onStart();

        StringBuilder contentBuilder = new StringBuilder();
        StringBuilder reasoningBuilder = new StringBuilder();
        AtomicReference<AssistantMessage> lastAssistantRef = new AtomicReference<>();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            agent.streamMessages(messages)
                    .doOnNext(message -> handleStreamMessage(message, callback, contentBuilder,
                            reasoningBuilder, lastAssistantRef, objectMapper))
                    .doOnError(error -> callback.onError(error))
                    .doOnComplete(() -> {
                        SpiChatResponse response = SpiChatResponse.builder()
                                .content(contentBuilder.toString())
                                .reasoningContent(reasoningBuilder.toString())
                                .build();
                        callback.onComplete(response);
                    })
                    .blockLast();
        } catch (Exception e) {
            callback.onError(e);
            throw new RuntimeException("Alibaba agent streaming failed: " + e.getMessage(), e);
        }

        String finalText = contentBuilder.toString();
        if (finalText.isEmpty() && lastAssistantRef.get() != null) {
            finalText = lastAssistantRef.get().getText();
        }
        return new Reply(ReplyType.TEXT, finalText != null ? finalText : "");
    }

    private void handleStreamMessage(Message message, SpiStreamingCallback callback,
                                     StringBuilder contentBuilder, StringBuilder reasoningBuilder,
                                     AtomicReference<AssistantMessage> lastAssistantRef,
                                     ObjectMapper objectMapper) {
        if (!(message instanceof AssistantMessage am)) {
            return;
        }
        lastAssistantRef.set(am);

        String text = am.getText();
        String reasoning = extractReasoningContent(am);

        if (reasoning != null && !reasoning.isEmpty()) {
            reasoningBuilder.append(reasoning);
            callback.onReasoningChunk(reasoning);
        }
        if (text != null && !text.isEmpty()) {
            contentBuilder.append(text);
            callback.onChunk(text);
        }

        if (am.hasToolCalls()) {
            for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                SpiToolCall spiToolCall = toSpiToolCall(tc, objectMapper);
                if (spiToolCall != null) {
                    callback.onToolCall(spiToolCall);
                }
            }
        }
    }

    private String extractReasoningContent(AssistantMessage message) {
        Map<String, Object> metadata = message.getMetadata();
        if (metadata == null) {
            return null;
        }
        Object reasoning = metadata.get("reasoningContent");
        if (reasoning instanceof String s && !s.isEmpty()) {
            return s;
        }
        return null;
    }

    private SpiToolCall toSpiToolCall(AssistantMessage.ToolCall tc, ObjectMapper objectMapper) {
        try {
            Map<String, Object> args = objectMapper.readValue(tc.arguments(), new TypeReference<>() {});
            return SpiToolCall.builder()
                    .id(tc.id())
                    .name(tc.name())
                    .arguments(args)
                    .build();
        } catch (Exception e) {
            return SpiToolCall.builder()
                    .id(tc.id())
                    .name(tc.name())
                    .arguments(Map.of())
                    .build();
        }
    }

    @Override
    public Reply resume(TaskContext ctx, SpiChatRequest request,
                        ApprovalTicket ticket, ApprovalResolution resolution) {
        ReactAgent agent = reactAgentFactory.get(ctx);

        List<SpiMessage> messages = new ArrayList<>(request.getMessages());
        ToolSubSystem toolSubSystem = ctx.getSubSystem("tool");

        for (ApprovalItem item : ticket.getItems()) {
            ApprovalStatus status = resolution.getDecisions().get(item.getToolCallId());
            String result = (status == ApprovalStatus.APPROVED)
                    ? executeToolCall(toolSubSystem, item)
                    : "REJECTED by operator";
            messages.add(SpiMessage.tool(buildToolResultJson(item.getToolCallId(), item.getToolName(), result)));
        }

        List<Message> springMessages = SpiMessageConverter.toSpringMessages(messages);
        try {
            AssistantMessage result = agent.call(springMessages);
            return new Reply(ReplyType.TEXT, result.getText());
        } catch (com.alibaba.cloud.ai.graph.exception.GraphRunnerException e) {
            throw new RuntimeException("Alibaba agent resume failed: " + e.getMessage(), e);
        }
    }

    private String executeToolCall(ToolSubSystem toolSubSystem, ApprovalItem item) {
        if (toolSubSystem == null) {
            return "Error: tool subsystem not available";
        }
        return toolSubSystem.getToolCallbacks().stream()
                .filter(tc -> tc.getToolDefinition().name().equals(item.getToolName()))
                .findFirst()
                .map(tc -> {
                    try {
                        return tc.call(item.getArgumentsJson());
                    } catch (Exception e) {
                        return "Error: " + e.getMessage();
                    }
                })
                .orElse("Error: tool not found: " + item.getToolName());
    }

    private String buildToolResultJson(String toolCallId, String toolName, String result) {
        try {
            return new ObjectMapper().writeValueAsString(Map.of(
                    "toolCallId", toolCallId,
                    "toolName", toolName,
                    "result", result
            ));
        } catch (Exception e) {
            return "{\"toolCallId\":\"" + toolCallId + "\",\"toolName\":\"" + toolName
                    + "\",\"result\":\"" + result.replace("\"", "\\\"") + "\"}";
        }
    }

    @Override
    public String name() {
        return "alibaba";
    }
}
