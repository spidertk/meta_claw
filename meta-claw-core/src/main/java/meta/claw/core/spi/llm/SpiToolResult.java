package meta.claw.core.spi.llm;

import lombok.Builder;

@Builder
public record SpiToolResult(String toolCallId, boolean success, String content, String errorMessage) {
}
