package meta.claw.core.spi.llm;

import lombok.Builder;

@Builder
public record Usage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
}
