package meta.claw.core.spi.llm;

import lombok.Builder;
import java.util.Map;

@Builder
public record SpiToolCall(String id, String name, Map<String, Object> arguments) {
}
