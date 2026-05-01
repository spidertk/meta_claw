package meta.claw.core.spi.llm;

import lombok.Builder;

@Builder
public record ToolDefinition(String name, String description, JsonSchema parameters) {
}
