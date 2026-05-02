package meta.claw.core.spi.llm;

import lombok.Builder;

@Builder
public record SpiToolDefinition(String name, String description, SpiJsonSchema parameters) {
}
