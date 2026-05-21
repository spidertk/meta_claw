package meta.claw.core.llm;

import lombok.Builder;

@Builder
public record SpiProviderMeta(String name, String model, String baseUrl) {
}
