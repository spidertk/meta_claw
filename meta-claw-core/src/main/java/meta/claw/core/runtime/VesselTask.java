package meta.claw.core.runtime;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * 任务参数 DTO。
 * <p>单次 chat()/execute() 调用的输入参数。</p>
 */
@Getter
@Builder
public class VesselTask {

    private final String taskId;
    private final String vesselId;
    private final String sessionId;
    private final String userMessage;

    @Builder.Default
    private final Instant createdAt = Instant.now();
}
