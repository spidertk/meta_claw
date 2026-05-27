package meta.claw.core.llm;

import lombok.Builder;
import meta.claw.core.tool.SpiToolCall;

import java.util.List;
import java.util.Map;

@Builder
public record SpiChatResponse(String content, String reasoningContent, List<SpiToolCall> toolCalls, SpiUsage usage, Map<String, Object> metadata) {
}
