package meta.claw.core.tool;

import lombok.Builder;
import meta.claw.core.llm.SpiJsonSchema;

@Builder
public record SpiToolDefinition(String name, String description, SpiJsonSchema parameters) {
}
