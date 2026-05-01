package meta.claw.core.spi.llm;

import lombok.Builder;

@Builder
public record ToolResult(String toolCallId, boolean success, String content, String errorMessage) {
}
