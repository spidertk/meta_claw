package meta.claw.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 出站消息模型
 * 封装了系统处理后需要发回给用户或群组的回复内容，用于统一向下游渠道输出
 */
@Builder
@Getter
@Setter
@AllArgsConstructor
public class OutboundMessage {

    /**
     * 目标渠道类型，例如：wechat、slack、web 等，决定由哪个具体渠道实现发送
     */
    private String channel;

    /**
     * 目标聊天会话标识，单聊为用户间会话 ID，群聊为群组 ID
     */
    private String chatId;

    /**
     * 接收者用户唯一标识，用于点对点消息或群聊中需要 @ 特定成员的场景
     */
    private String userId;

    /**
     * 系统生成的回复内容对象，包含回复类型（文本、图片、语音等）和实际内容
     */
    private String reply;
}
