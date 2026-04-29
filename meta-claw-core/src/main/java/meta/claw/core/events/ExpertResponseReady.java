package meta.claw.core.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import meta.claw.core.model.Context;
import meta.claw.core.model.Reply;

/**
 * Expert 回复就绪领域事件
 * 当 Expert 模块完成对用户消息的处理并生成回复后触发，用于通知下游组件发送回复
 */
@Getter
@AllArgsConstructor
public class ExpertResponseReady {

    /** 渠道类型，标识回复需要发送到的目标渠道（如 weixin、slack、douyin 等） */
    private final String channelType;

    /** Expert 生成的回复对象，包含回复类型与内容 */
    private final Reply reply;

    /** 原始消息上下文，用于追溯请求来源及携带会话相关信息 */
    private final Context context;
}
