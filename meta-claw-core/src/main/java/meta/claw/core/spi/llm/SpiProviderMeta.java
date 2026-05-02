package meta.claw.core.spi.llm;

import lombok.Builder;

@Builder
public record SpiProviderMeta(String name, String model, String baseUrl) {
}
