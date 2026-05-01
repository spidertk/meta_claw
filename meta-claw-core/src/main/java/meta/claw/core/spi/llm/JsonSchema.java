package meta.claw.core.spi.llm;

import lombok.Builder;
import java.util.Map;

@Builder
public record JsonSchema(String type, String description, Map<String, JsonSchema> properties) {
}
