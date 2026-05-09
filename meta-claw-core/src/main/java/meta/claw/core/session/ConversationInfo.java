package meta.claw.core.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 会话信息
 * 描述一个对话会话的概要信息，用于列表展示
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ConversationInfo {

    /**
     * 会话标识（即 sessionKey）
     */
    private String sessionId;

    /**
     * 会话创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 会话最后更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 消息数量
     */
    private int messageCount;
}
