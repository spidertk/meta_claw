package meta.claw.core.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import meta.claw.core.tool.SpiToolCall;

import java.util.List;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SpiMessage {
    private String role;
    private String content;
    private String reasoningContent;
    private List<SpiToolCall> toolCalls;
    private String toolCallId;
    private String toolName;
    private List<MediaPart> mediaParts;

    public static SpiMessage system(String content) {
        return SpiMessage.builder().role("system").content(content).build();
    }

    public static SpiMessage user(String content) {
        return SpiMessage.builder().role("user").content(content).build();
    }

    public static SpiMessage user(String content, List<MediaPart> mediaParts) {
        return SpiMessage.builder().role("user").content(content).mediaParts(mediaParts).build();
    }

    public static SpiMessage assistant(String content) {
        return SpiMessage.builder().role("assistant").content(content).build();
    }

    public static SpiMessage assistant(String content, List<SpiToolCall> toolCalls) {
        return SpiMessage.builder().role("assistant").content(content).toolCalls(toolCalls).build();
    }

    public static SpiMessage assistant(String content, String reasoningContent, List<SpiToolCall> toolCalls) {
        return SpiMessage.builder()
                .role("assistant")
                .content(content)
                .reasoningContent(reasoningContent)
                .toolCalls(toolCalls)
                .build();
    }

    public static SpiMessage tool(String content) {
        return SpiMessage.builder().role("tool").content(content).build();
    }

    public static SpiMessage tool(String content, String toolCallId, String toolName) {
        return SpiMessage.builder()
                .role("tool")
                .content(content)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .build();
    }
}
