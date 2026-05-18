package meta.claw.core.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 短期会话聚合。
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class SessionMemory {
    private String sessionId;
    private LocalDateTime updatedAt;
    private int messageCount;
    private String summary;
    private List<MemoryMessage> messages;
}
