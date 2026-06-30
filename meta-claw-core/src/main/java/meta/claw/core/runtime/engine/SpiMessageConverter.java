package meta.claw.core.runtime.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import meta.claw.core.llm.MediaPart;
import meta.claw.core.llm.SpiMessage;
import meta.claw.core.tool.SpiToolCall;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.MimeType;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 在 meta-claw {@link SpiMessage} 与 Spring AI {@link Message} 之间做双向转换。
 *
 * <p>特别注意 tool 消息：必须生成带正确 {@code toolCallId} 的 {@link ToolResponseMessage}，
 * 否则 SAA 的条件边无法把 tool 结果与 assistant 的 tool_calls 对应起来。</p>
 */
public final class SpiMessageConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SpiMessageConverter() {}

    public static List<Message> toSpringMessages(List<SpiMessage> messages) {
        List<Message> result = new ArrayList<>(messages.size());
        for (SpiMessage m : messages) {
            result.add(toSpringMessage(m));
        }
        return result;
    }

    public static Message toSpringMessage(SpiMessage m) {
        String role = m.getRole() != null ? m.getRole().toLowerCase() : "";
        return switch (role) {
            case "system" -> new SystemMessage(m.getContent());
            case "user" -> {
                List<org.springframework.ai.content.Media> media = toSpringMedia(m.getMediaParts());
                if (media.isEmpty()) {
                    yield new UserMessage(m.getContent());
                } else {
                    yield UserMessage.builder()
                            .text(m.getContent())
                            .media(media)
                            .build();
                }
            }
            case "assistant" -> {
                java.util.Map<String, Object> properties = new java.util.HashMap<>();
                if (m.getReasoningContent() != null && !m.getReasoningContent().isEmpty()) {
                    properties.put("reasoningContent", m.getReasoningContent());
                }
                yield AssistantMessage.builder()
                        .content(m.getContent() != null ? m.getContent() : "")
                        .properties(properties)
                        .toolCalls(toSpringToolCalls(m.getToolCalls()))
                        .build();
            }
            case "tool" -> {
                String toolCallId = m.getToolCallId();
                String toolName = m.getToolName();
                String result = m.getContent();
                if (toolCallId == null || toolName == null) {
                    ToolResult tr = parseToolResultJson(m.getContent());
                    toolCallId = tr.toolCallId();
                    toolName = tr.toolName();
                    result = tr.result();
                }
                yield ToolResponseMessage.builder()
                        .responses(List.of(new ToolResponseMessage.ToolResponse(
                                toolCallId != null ? toolCallId : "unknown",
                                toolName != null ? toolName : "unknown",
                                result)))
                        .build();
            }
            default -> new UserMessage(m.getContent());
        };
    }

    private static List<org.springframework.ai.content.Media> toSpringMedia(List<MediaPart> parts) {
        if (parts == null || parts.isEmpty()) {
            return List.of();
        }
        return parts.stream()
                .map(part -> {
                    String mimeType = part.getMimeType() != null ? part.getMimeType() : "image/png";
                    return new org.springframework.ai.content.Media(
                            MimeType.valueOf(mimeType),
                            URI.create(part.getUrl()));
                })
                .collect(Collectors.toList());
    }

    private static List<AssistantMessage.ToolCall> toSpringToolCalls(List<SpiToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return List.of();
        }
        return toolCalls.stream()
                .map(tc -> new AssistantMessage.ToolCall(
                        tc.getId(),
                        "function",
                        tc.getName(),
                        toArgumentsJson(tc.getArguments())))
                .collect(Collectors.toList());
    }

    private static String toArgumentsJson(Map<String, Object> arguments) {
        if (arguments == null) {
            return "{}";
        }
        try {
            return MAPPER.writeValueAsString(arguments);
        } catch (Exception e) {
            return "{}";
        }
    }

    private record ToolResult(String toolCallId, String toolName, String result) {}

    private static ToolResult parseToolResultJson(String json) {
        if (json == null || json.isBlank()) {
            return new ToolResult("unknown", "unknown", "");
        }
        try {
            Map<String, Object> map = MAPPER.readValue(json, new TypeReference<>() {});
            return new ToolResult(
                    String.valueOf(map.getOrDefault("toolCallId", "unknown")),
                    String.valueOf(map.getOrDefault("toolName", "unknown")),
                    String.valueOf(map.getOrDefault("result", "")));
        } catch (Exception e) {
            return new ToolResult("unknown", "unknown", json);
        }
    }
}
