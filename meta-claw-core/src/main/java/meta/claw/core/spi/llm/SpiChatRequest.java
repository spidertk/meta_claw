package meta.claw.core.spi.llm;

import lombok.Builder;
import java.util.List;
import java.util.Map;

@Builder
public record SpiChatRequest(List<SpiMessage> messages, List<SpiToolDefinition> tools, Map<String, Object> options) {
}
