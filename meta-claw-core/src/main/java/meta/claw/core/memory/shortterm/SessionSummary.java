package meta.claw.core.memory.shortterm;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 短期记忆会话摘要。
 */
@Getter
@Builder
public class SessionSummary {
    private String sessionId;
    private LocalDateTime updatedAt;
    private int messageCount;
}
