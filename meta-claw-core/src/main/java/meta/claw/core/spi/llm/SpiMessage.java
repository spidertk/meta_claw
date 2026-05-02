package meta.claw.core.spi.llm;

import lombok.Builder;
import java.util.List;

@Builder
public record SpiMessage(String role, String content, List<SpiToolCall> toolCalls) {
    public static SpiMessage system(String content) {
        return new SpiMessage("system", content, null);
    }
    public static SpiMessage user(String content) {
        return new SpiMessage("user", content, null);
    }
    public static SpiMessage assistant(String content) {
        return new SpiMessage("assistant", content, null);
    }
    public static SpiMessage assistant(String content, List<SpiToolCall> toolCalls) {
        return new SpiMessage("assistant", content, toolCalls);
    }
    public static SpiMessage tool(String content) {
        return new SpiMessage("tool", content, null);
    }
}
