package meta.claw.core.runtime;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 单步执行记录。
 */
@Getter
@Builder
public class StepRecord {

    private final int stepNumber;
    private final String action;
    private final String description;

    @Builder.Default
    private final Instant timestamp = Instant.now();
}
