package meta.claw.core.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import meta.claw.core.model.Context;

/**
 * 用户消息接收领域事件
 * 当系统接收到用户发送的消息时触发，用于通知后续处理流程
 */
@Getter
@AllArgsConstructor
public class UserMessageReceived {

    /** 消息上下文，包含用户消息内容、类型及扩展参数 */
    private final Context context;

    /** 会话唯一标识，用于关联同一对话链路中的多条消息 */
    private final String sessionId;

    /** 渠道类型，标识消息来源（如 weixin、slack、douyin 等） */
    private final String channelType;
}
