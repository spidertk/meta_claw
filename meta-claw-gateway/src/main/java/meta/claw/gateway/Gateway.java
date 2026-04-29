package meta.claw.gateway;

import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.eventbus.EventBusWrapper;
import meta.claw.core.events.ExpertResponseReady;
import meta.claw.core.events.UserMessageReceived;
import meta.claw.core.model.Context;
import meta.claw.core.model.ContextType;
import meta.claw.gateway.channel.Channel;
import meta.claw.gateway.channel.ChannelRegistry;
import meta.claw.gateway.channel.ChatMessage;

/**
 * Gateway 中央控制器
 * 作为系统消息出入口的核心协调者，负责：
 * 1. 管理所有 Channel 实例的注册与生命周期；
 * 2. 接收外部入站消息，构造上下文并发布 UserMessageReceived 事件；
 * 3. 订阅 ExpertResponseReady 事件，将 Expert 生成的回复路由到对应渠道发送。
 */
@Slf4j
public class Gateway {

    /**
     * 渠道注册表，维护所有已注册 Channel 实例的映射关系
     */
    private final ChannelRegistry registry;

    /**
     * 事件总线封装器，用于发布和订阅异步领域事件
     */
    private final EventBusWrapper eventBus;

    /**
     * 构造函数
     * 初始化 Gateway 并自动将自身注册为事件总线的订阅者，以便接收 ExpertResponseReady 事件。
     *
     * @param registry 渠道注册表实例
     * @param eventBus 事件总线封装器实例
     */
    public Gateway(ChannelRegistry registry, EventBusWrapper eventBus) {
        this.registry = registry;
        this.eventBus = eventBus;
        // 将 Gateway 自身注册为 EventBus 订阅者，订阅 ExpertResponseReady 等事件
        this.eventBus.register(this);
        log.info("[Gateway] Gateway 已初始化并注册为 EventBus 订阅者");
    }

    /**
     * 注册并启动渠道
     * 将指定 Channel 实例注册到 ChannelRegistry，并调用其 startup 方法完成初始化。
     *
     * @param channel 待注册并启动的渠道实例
     */
    public void registerChannel(Channel channel) {
        registry.register(channel);
        try {
            channel.startup();
            log.info("[Gateway] 渠道注册并启动成功: {}", channel.getChannelType());
        } catch (Exception e) {
            log.error("[Gateway] 渠道启动失败: {}, 异常: {}", channel.getChannelType(), e.getMessage(), e);
        }
    }

    /**
     * 处理入站消息（完整版）
     * 当收到外部渠道的原始消息时调用，根据 ChatMessage 构造 Context，
     * 并发布 UserMessageReceived 事件通知下游组件处理。
     *
     * @param msg         封装后的内部聊天消息对象
     * @param channelType 消息来源渠道类型，例如：weixin、slack 等
     */
    public void onInboundMessage(ChatMessage msg, String channelType) {
        // 构造消息上下文，设置消息类型、内容、渠道类型等基础属性
        Context context = new Context(ContextType.TEXT, msg.getContent());
        context.setChannelType(channelType);
        context.setSessionId(msg.getOtherUserId());
        context.setReceiver(msg.getOtherUserId());
        context.setGroup(msg.isGroup());

        // 将原始 ChatMessage 存入扩展参数，供下游处理流程使用
        context.getKwargs().put("msg", msg);

        // 发布用户消息接收事件，触发后续 Expert 处理流程
        eventBus.post(new UserMessageReceived(context, msg.getOtherUserId(), channelType));
        log.debug("[Gateway] 入站消息已发布事件, channelType={}, sessionId={}", channelType, msg.getOtherUserId());
    }

    /**
     * 处理入站消息（简化版）
     * 针对仅包含文本内容和用户 ID 的简化场景，直接构造 Context 并发布 UserMessageReceived 事件。
     *
     * @param content     消息文本内容
     * @param userId      发送者用户唯一标识，同时用作会话 ID
     * @param channelType 消息来源渠道类型，例如：weixin、slack 等
     */
    public void onInboundMessage(String content, String userId, String channelType) {
        // 构造简化版消息上下文
        Context context = new Context(ContextType.TEXT, content);
        context.setChannelType(channelType);
        context.setSessionId(userId);
        context.setReceiver(userId);

        // 发布用户消息接收事件
        eventBus.post(new UserMessageReceived(context, userId, channelType));
        log.debug("[Gateway] 简化入站消息已发布事件, channelType={}, userId={}", channelType, userId);
    }

    /**
     * 订阅 Expert 回复就绪事件
     * 当 Expert 模块完成消息处理并生成回复后触发，Gateway 根据事件中的渠道类型
     * 从 ChannelRegistry 查找对应渠道，并调用其 send 方法将回复发送给用户。
     *
     * @param event Expert 回复就绪领域事件，包含回复内容、上下文及目标渠道类型
     */
    @Subscribe
    public void onResponseReady(ExpertResponseReady event) {
        String channelType = event.getChannelType();
        Channel channel = registry.get(channelType);

        if (channel != null) {
            channel.send(event.getReply(), event.getContext());
            log.info("[Gateway] 回复已路由到渠道发送, channelType={}, sessionId={}",
                    channelType, event.getContext().getSessionId());
        } else {
            log.warn("[Gateway] 未找到对应渠道，无法发送回复, channelType={}", channelType);
        }
    }
}
