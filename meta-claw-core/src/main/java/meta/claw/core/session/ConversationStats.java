package meta.claw.core.session;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 会话统计信息，包含消息数量、角色分布、时间范围和文件大小。
 */
@Getter
@Builder
public class ConversationStats {
    private int messageCount;
    private int userMessages;
    private int assistantMessages;
    private int systemMessages;
    private LocalDateTime firstMessage;
    private LocalDateTime lastMessage;
    private long fileSizeBytes;
}
