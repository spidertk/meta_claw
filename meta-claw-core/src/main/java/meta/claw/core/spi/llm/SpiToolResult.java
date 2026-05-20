package meta.claw.core.spi.llm;

import lombok.Builder;

/**
 * 工具执行结果。
 */
@Builder
public record SpiToolResult(String toolCallId, boolean success, String content, String errorMessage) {
}
