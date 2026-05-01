package meta.claw.core.spi.llm;

import lombok.Builder;
import java.util.List;
import java.util.Map;

@Builder
public record ChatRequest(List<Message> messages, List<ToolDefinition> tools, Map<String, Object> options) {
}
