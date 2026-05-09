package meta.claw.core.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 对话消息记录
 * 封装单条聊天消息的内容、角色、时间戳及相关元数据
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ChatMessage {

    /**
     * 消息唯一标识
     */
    private String messageId;

    /**
     * 所属会话键
     */
    private String sessionKey;

    /**
     * 消息角色：system / user / assistant / tool
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 消息类型：text / image / file / audio 等
     */
    private String messageType;

    /**
     * 发送者用户标识（user 角色时有效）
     */
    private String userId;

    /**
     * 发送者用户名
     */
    private String username;

    /**
     * 回复的消息标识
     */
    private String replyTo;

    /**
     * 关联的专家/代理名称
     */
    private String vesselName;

    /**
     * 消息时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 扩展元数据
     */
    private Map<String, Object> metadata;
}
