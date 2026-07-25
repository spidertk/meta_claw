package meta.claw.app;

import meta.claw.core.eventbus.EventBusWrapper;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.ChannelRegistry;
import meta.claw.gateway.channel.ChannelVesselRouter;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.runtime.AgentLoop;
import meta.claw.core.runtime.VesselManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Meta-Claw 核心配置类
 * <p>
 * 负责手动装配系统中所有核心 Bean，包括事件总线、网关、Vessel 管理及渠道等组件。
 * 所有 Bean 均采用显式声明方式，便于集中管理和调试。
 * </p>
 */
@Configuration
public class AppConfig {

    /**
     * 事件总线包装器 Bean
     * <p>
     * 基于 Guava AsyncEventBus 实现，为系统各模块提供异步事件发布与订阅能力。
     * 是 Vessel、Gateway、AgentLoop 等组件间解耦通信的核心基础设施。
     * </p>
     *
     * @return EventBusWrapper 实例
     */
    @Bean
    public EventBusWrapper eventBusWrapper() {
        return new EventBusWrapper();
    }

    /**
     * 渠道注册表 Bean
     * <p>
     * 维护所有已注册渠道（Channel）实例的映射关系，支持按渠道类型快速查找。
     * 使用 ConcurrentHashMap 保证线程安全。
     * </p>
     *
     * @return ChannelRegistry 实例
     */
    @Bean
    public ChannelRegistry channelRegistry() {
        return new ChannelRegistry();
    }

    /**
     * 渠道 Vessel 路由器 Bean
     * <p>
     * 维护 {channelKey, chatKey} → vesselId 路由表，持久化于 .meta-claw/channels/routes.json，
     * 支持用户通过 /vessel 命令切换对话绑定的 Vessel。
     * </p>
     *
     * @return ChannelVesselRouter 实例
     */
    @Bean
    public ChannelVesselRouter channelVesselRouter() {
        Path routesFile = ProjectRootFinder.getMetaClawDir().resolve("channels").resolve("routes.json");
        return new ChannelVesselRouter(routesFile);
    }

    /**
     * 网关中央控制器 Bean
     * <p>
     * 作为系统消息出入口的核心协调者，负责渠道注册、入站消息处理及 Vessel 回复路由。
     * 初始化时自动注册为 EventBus 订阅者，监听 VesselResponseReady 事件。
     * </p>
     *
     * @param registry 渠道注册表
     * @param eventBus 事件总线包装器
     * @param router   渠道 Vessel 路由器
     * @return Gateway 实例
     */
    @Bean
    public Gateway gateway(ChannelRegistry registry, EventBusWrapper eventBus, ChannelVesselRouter router) {
        return new Gateway(registry, eventBus, router);
    }



    /**
     * Agent 事件循环处理器 Bean
     * <p>
     * 订阅 EventBus 上的 UserMessageReceived 事件，负责调度 Vessel 处理用户消息，
     * 并将 Vessel 生成的回复通过 VesselResponseReady 事件发布给 Gateway 发送。
     * </p>
     *
     * @param eventBus      事件总线包装器
     * @param vesselManager Vessel 管理器
     * @return AgentLoop 实例
     */
    @Bean
    public AgentLoop agentLoop(EventBusWrapper eventBus, VesselManager vesselManager) {
        return new AgentLoop(eventBus, vesselManager);
    }

}
