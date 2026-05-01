package meta.claw.core.spi.llm;

import lombok.Builder;
import java.util.List;

@Builder
public record Message(String role, String content, List<ToolCall> toolCalls) {
    public static Message system(String content) {
        return new Message("system", content, null);
    }
    public static Message user(String content) {
        return new Message("user", content, null);
    }
    public static Message assistant(String content) {
        return new Message("assistant", content, null);
    }
    public static Message assistant(String content, List<ToolCall> toolCalls) {
        return new Message("assistant", content, toolCalls);
    }
    public static Message tool(String content) {
        return new Message("tool", content, null);
    }
}
