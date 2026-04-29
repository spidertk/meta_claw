package meta.claw.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * 网关消息模型
 * 封装了从各外部渠道（如微信、Slack、Web 等）接收到的原始消息，用于在网关层统一流转和处理
 */
@Builder
@Getter
@Setter
@AllArgsConstructor
public class GatewayMessage {

    /**
     * 消息唯一标识，由上游渠道生成，用于去重、追踪和幂等控制
     */
    private String messageId;

    /**
     * 消息来源渠道类型，例如：wechat、slack、web、dingtalk 等
     */
    private String channel;

    /**
     * 聊天会话标识，单聊为用户间会话 ID，群聊为群组 ID
     */
    private String chatId;

    /**
     * 发送者用户唯一标识，由对应渠道提供
     */
    private String userId;

    /**
     * 消息正文内容，文本消息为纯文本，媒体类消息可能为文件路径或链接
     */
    private String content;

    /**
     * 内容类型，例如：text、voice、image、file 等，用于决定后续处理方式
     */
    private String contentType;

    /**
     * 当前消息关联的代理（Agent）标识，用于路由到特定专家或工作流
     */
    private String agentId;
}
