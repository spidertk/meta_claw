package meta.claw.core.runtime;

import com.google.common.eventbus.Subscribe;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.eventbus.EventBusWrapper;
import meta.claw.core.events.VesselResponseReady;
import meta.claw.core.events.UserMessageReceived;
import meta.claw.core.model.Context;
import meta.claw.core.model.Reply;
import meta.claw.core.model.ReplyType;
import meta.claw.core.model.VesselConfig;

import java.util.List;

/**
 * Agent 事件循环处理器
 * <p>
 * 作为 EventBus 的事件订阅者，负责监听用户消息事件，调度 Vessel 进行处理，
 * 并将 Vessel 生成的回复通过事件总线发布给下游组件。
 * </p>
 */
@Slf4j
public class AgentLoop {

    /**
     * 事件总线封装实例，用于发布和订阅领域事件
     */
    private final EventBusWrapper eventBus;

    /**
     * Vessel 管理器，负责提供可用 Vessel 的配置和运行时实例
     */
    private final VesselManager vesselManager;

    /**
     * 构造方法：初始化 AgentLoop 并注册为 EventBus 订阅者
     * <p>
     * 注册后，Guava EventBus 会通过反射识别带有 @Subscribe 注解的方法，
     * 当对应类型的事件被发布时自动调用。
     * </p>
     *
     * @param eventBus      事件总线封装实例
     * @param vesselManager Vessel 管理器实例
     */
    public AgentLoop(EventBusWrapper eventBus, VesselManager vesselManager) {
        this.eventBus = eventBus;
        this.vesselManager = vesselManager;
        // 将当前实例注册为事件总线订阅者
        this.eventBus.register(this);
        log.info("AgentLoop 已注册为 EventBus 订阅者");
    }

    /**
     * 处理用户消息事件
     * <p>
     * 当系统接收到用户消息时触发，负责：
     * 1. 从事件中提取上下文、会话 ID 和渠道类型；
     * 2. 调用路由策略确定目标 Vessel；
     * 3. 获取 Vessel 运行时实例并执行对话；
     * 4. 将 Vessel 回复包装为事件发布到总线。
     * 若找不到可用 Vessel 或处理过程发生异常，将发布 ERROR 类型的回复事件。
     * </p>
     *
     * @param event 用户消息接收事件，包含消息上下文、会话标识和渠道信息
     */
    @Subscribe
    public void onUserMessage(UserMessageReceived event) {
        // 从事件中提取核心信息
        Context context = event.getContext();
        String sessionId = event.getSessionId();
        String channelType = event.getChannelType();

        log.info("AgentLoop 接收到用户消息: sessionId={}, channelType={}, contentLength={}",
                sessionId, channelType, context.getContent() != null ? context.getContent().length() : 0);

        try {
            // 步骤 1：确定目标 Vessel
            String targetVesselId = determineTargetVessel();
            if (targetVesselId == null) {
                log.warn("未找到可用的 Vessel，无法处理用户消息: sessionId={}", sessionId);
                // 发布 ERROR 类型的回复事件，提示当前无可用 Vessel
                Reply errorReply = new Reply(ReplyType.ERROR, "当前没有可用的 Vessel，请稍后重试");
                eventBus.post(new VesselResponseReady(channelType, errorReply, context));
                return;
            }
            log.info("路由决策完成: 目标 Vessel={}, sessionId={}", targetVesselId, sessionId);

            // 步骤 2：获取 Vessel 运行时实例
            VesselRuntime runtime = vesselManager.getRuntime(targetVesselId);
            if (runtime == null) {
                log.warn("Vessel 运行时实例未注册: vesselId={}, sessionId={}", targetVesselId, sessionId);
                // 发布 ERROR 类型的回复事件，提示 Vessel 运行时未就绪
                Reply errorReply = new Reply(ReplyType.ERROR, "目标 Vessel 尚未就绪，请稍后重试");
                eventBus.post(new VesselResponseReady(channelType, errorReply, context));
                return;
            }

            // 步骤 3：调用 Vessel 进行对话处理
            Reply reply = runtime.chat(context.getContent());
            log.info("Vessel 处理完成: vesselId={}, replyType={}, sessionId={}",
                    targetVesselId, reply.getType(), sessionId);

            // 步骤 4：发布回复就绪事件，通知下游组件发送回复
            eventBus.post(new VesselResponseReady(channelType, reply, context));
        } catch (Exception e) {
            // 捕获处理过程中的所有异常，避免事件总线因订阅者异常而中断
            log.error("处理用户消息时发生异常: sessionId={}, errorMessage={}", sessionId, e.getMessage(), e);
            // 发布 ERROR 类型的回复事件，向用户展示友好的错误提示
            Reply errorReply = new Reply(ReplyType.ERROR, "消息处理异常，请稍后重试");
            eventBus.post(new VesselResponseReady(channelType, errorReply, context));
        }
    }

    /**
     * 确定目标 Vessel
     * <p>
     * 当前实现采用最简单的策略：返回第一个可用的 Vessel ID。
     * 后续可扩展为基于意图识别、负载均衡或权重匹配的复杂路由策略。
     * </p>
     *
     * @return 第一个可用 Vessel 的 ID；若不存在任何可用 Vessel，则返回 null
     */
    public String determineTargetVessel() {
        // 获取所有已加载的 Vessel 配置列表
        List<VesselConfig> availableVessels = vesselManager.listAvailableVessels();
        if (availableVessels == null || availableVessels.isEmpty()) {
            log.debug("当前系统中没有可用的 Vessel");
            return null;
        }
        // 返回列表中的第一个 Vessel 的 ID
        String firstVesselId = availableVessels.get(0).getId();
        log.debug("路由策略选择 Vessel: {}", firstVesselId);
        return firstVesselId;
    }

    /**
     * 启动 AgentLoop
     * <p>
     * 当前为空实现，预留用于后续扩展启动逻辑，
     * 例如：预加载模型、初始化会话池、启动健康检查等。
     * </p>
     */
    public void start() {
        // 预留扩展：启动时的初始化逻辑
        log.info("AgentLoop 启动完成");
    }
}
