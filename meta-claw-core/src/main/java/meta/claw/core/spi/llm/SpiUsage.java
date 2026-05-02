package meta.claw.core.spi.llm;

import lombok.Builder;

@Builder
public record SpiUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
}
