package meta.claw.session.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户会话实体
 * 封装了用户在系统中的完整会话状态，包括会话标识、用户身份、聊天模式、关联专家等信息
 */
@Builder
@Getter
@Setter
public class UserSession {

    /**
     * 会话唯一标识键，用于定位特定会话
     */
    private String sessionKey;

    /**
     * 用户唯一标识
     */
    private String userId;

    /**
     * 会话来源渠道，例如：web、wechat、slack 等
     */
    private String source;

    /**
     * 当前绑定的代理标识
     */
    private String agentId;

    /**
     * 当前聊天模式：单聊或群聊
     */
    private ChatMode mode;

    /**
     * 目标专家标识，单聊模式下有效
     */
    private String targetExpert;

    /**
     * 群组会话标识，群聊模式下有效
     */
    private String groupSessionId;

    /**
     * 是否开启调试模式
     */
    private boolean debugMode;

    /**
     * 最后活动时间，用于判断会话是否过期
     */
    private LocalDateTime lastActivity;

    /**
     * 会话创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新最后活动时间为当前时间
     * 通常在用户发送消息或进行交互时调用，以保持会话活跃
     */
    public void touch() {
        this.lastActivity = LocalDateTime.now();
    }

    /**
     * 设置为单聊模式
     * 同时将群聊相关字段清空
     */
    public void setSingleMode() {
        this.mode = ChatMode.SINGLE;
        this.groupSessionId = null;
    }

    /**
     * 设置为群聊模式
     * 同时将单聊目标专家字段清空
     */
    public void setGroupMode() {
        this.mode = ChatMode.GROUP;
        this.targetExpert = null;
    }

    /**
     * 切换调试模式的开关状态
     * 开启时关闭，关闭时开启
     */
    public void toggleDebugMode() {
        this.debugMode = !this.debugMode;
    }
}
