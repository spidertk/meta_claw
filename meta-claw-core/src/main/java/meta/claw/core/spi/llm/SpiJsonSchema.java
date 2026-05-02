package meta.claw.core.spi.llm;

import lombok.Builder;
import java.util.Map;

@Builder
public record SpiJsonSchema(String type, String description, Map<String, SpiJsonSchema> properties) {
}
