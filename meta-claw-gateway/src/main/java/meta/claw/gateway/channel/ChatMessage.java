package meta.claw.gateway.channel;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内部聊天消息模型
 * 统一封装不同外部渠道（微信、Slack、钉钉等）的原始消息，供 Channel 层内部处理和路由使用。
 * 填好必填项后即可接入 ChatChannel，并支持插件系统。
 */
@Builder
@Getter
@Setter
@AllArgsConstructor
public class ChatMessage {

    /**
     * 消息唯一标识，由上游渠道生成（必填）
     */
    private String msgId;

    /**
     * 消息创建时间，用于排序和时效判断
     */
    private LocalDateTime createTime;

    /**
     * 消息内容类型，例如：TEXT、VOICE、IMAGE、FILE 等（必填）
     */
    private String contentType;

    /**
     * 消息正文内容；如果是声音或图片，这里可能是文件路径或临时链接（必填）
     */
    private String content;

    /**
     * 发送者用户唯一标识（必填）
     */
    private String fromUserId;

    /**
     * 发送者用户昵称，用于展示和日志记录
     */
    private String fromUserNickname;

    /**
     * 接收者用户唯一标识（必填）
     */
    private String toUserId;

    /**
     * 接收者用户昵称
     */
    private String toUserNickname;

    /**
     * 对方用户标识：
     * 如果当前用户是发送者，则此字段为接收者 ID；
     * 如果当前用户是接收者，则此字段为发送者 ID；
     * 如果是群消息，则此字段固定为群 ID（必填）
     */
    private String otherUserId;

    /**
     * 对方用户昵称，逻辑同 otherUserId
     */
    private String otherUserNickname;

    /**
     * 当前消息是否由自己（机器人）发出，用于过滤自身消息避免循环处理
     */
    private boolean myMsg;

    /**
     * 自身的展示名称；在群聊中设置群昵称时，该字段表示群昵称
     */
    private String selfDisplayName;

    /**
     * 是否为群聊消息（群聊场景必填）
     */
    private boolean isGroup;

    /**
     * 当前消息是否 @ 了机器人自己，用于决定是否触发回复
     */
    private boolean isAt;

    /**
     * 实际发送者用户 ID；仅在群聊场景中存在，表示群内某个具体成员（群聊必填）
     */
    private String actualUserId;

    /**
     * 实际发送者用户昵称；仅在群聊场景中存在
     */
    private String actualUserNickname;

    /**
     * 消息中 @ 的成员列表，元素为用户 ID 字符串；群聊中用于识别被 @ 的人
     */
    private List<String> atList;
}
