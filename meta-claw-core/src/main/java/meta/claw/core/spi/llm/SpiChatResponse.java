package meta.claw.core.spi.llm;

import lombok.Builder;
import java.util.List;
import java.util.Map;

@Builder
public record SpiChatResponse(String content, List<SpiToolCall> toolCalls, SpiUsage usage, Map<String, Object> metadata) {
}
