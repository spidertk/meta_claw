package meta.claw.core.spi.llm;

import lombok.Builder;

@Builder
public record ProviderMeta(String name, String model, String baseUrl) {
}
