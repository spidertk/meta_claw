package meta.claw.core.memory.shortterm;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 消息过滤条件，用于按角色、用户、类型、时间范围检索历史消息。
 */
@Getter
@Builder
public class MessageFilter {
    private String role;
    private String userId;
    private String messageType;
    private LocalDateTime after;
    private LocalDateTime before;
}
