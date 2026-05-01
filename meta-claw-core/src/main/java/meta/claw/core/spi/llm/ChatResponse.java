package meta.claw.core.spi.llm;

import lombok.Builder;
import java.util.List;
import java.util.Map;

@Builder
public record ChatResponse(String content, List<ToolCall> toolCalls, Usage usage, Map<String, Object> metadata) {
}
