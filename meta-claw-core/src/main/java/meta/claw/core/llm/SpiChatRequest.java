package meta.claw.core.llm;

import lombok.Builder;
import meta.claw.core.tool.SpiToolDefinition;

import java.util.List;
import java.util.Map;

@Builder
public record SpiChatRequest(String vesselName,List<SpiMessage> messages, List<SpiToolDefinition> tools, Map<String, Object> options) {
}
