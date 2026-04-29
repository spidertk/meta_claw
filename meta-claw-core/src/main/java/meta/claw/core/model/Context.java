package meta.claw.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息上下文对象
 * 封装了用户发送的消息及其相关元数据，用于在系统内部传递和处理
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Context {

    /** 消息上下文类型 */
    private ContextType type;

    /** 消息内容 */
    private String content;

    /** 扩展参数键值对，用于存储额外的上下文信息 */
    private Map<String, Object> kwargs = new HashMap<>();

    /** 会话唯一标识 */
    private String sessionId;

    /** 消息接收者标识 */
    private String receiver;

    /** 渠道类型 */
    private String channelType;

    /** 是否为群聊消息的标记 */
    private boolean group;

    /** 期望的回复类型 */
    private ReplyType expectedReplyType;

    /**
     * 便捷构造函数
     * 仅初始化消息类型和内容，其余字段使用默认值
     *
     * @param type    消息上下文类型
     * @param content 消息内容
     */
    public Context(ContextType type, String content) {
        this.type = type;
        this.content = content;
    }
}
