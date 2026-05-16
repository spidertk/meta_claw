package meta.claw.app;

import meta.claw.core.eventbus.EventBusWrapper;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.channel.ChannelRegistry;
import meta.claw.gateway.weixin.WeixinChannel;
import meta.claw.gateway.weixin.WeixinConfig;
import meta.claw.core.runtime.AgentLoop;
import meta.claw.core.runtime.VesselManager;
import meta.claw.core.runtime.VesselRuntime;
import meta.claw.core.config.VesselConfig;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
     * 微信渠道认证令牌，从 application.yml 中读取
     */
    @Value("${meta.claw.weixin.token}")
    private String weixinToken;

    /**
     * Vessel 配置目录路径，从 application.yml 中读取
     */
    @Value("${meta.claw.vessels.dir}")
    private String vesselsDir;

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
     * 网关中央控制器 Bean
     * <p>
     * 作为系统消息出入口的核心协调者，负责渠道注册、入站消息处理及 Vessel 回复路由。
     * 初始化时自动注册为 EventBus 订阅者，监听 VesselResponseReady 事件。
     * </p>
     *
     * @param registry 渠道注册表
     * @param eventBus 事件总线包装器
     * @return Gateway 实例
     */
    @Bean
    public Gateway gateway(ChannelRegistry registry, EventBusWrapper eventBus) {
        return new Gateway(registry, eventBus);
    }

    /**
     * Vessel 管理器 Bean
     * <p>
     * 负责扫描并加载 vessels/ 目录下的所有 Vessel 配置（vessel.md），
     * 维护 Vessel 配置及运行时实例的注册与查询。
     * Bean 创建时立即调用 loadVessels() 完成配置加载。
     * </p>
     *
     * @return VesselManager 实例
     */
    @Bean
    public VesselManager vesselManager() {
        VesselManager manager = new VesselManager(vesselsDir);
        manager.loadVessels();
        return manager;
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

    /**
     * 微信渠道 Bean
     * <p>
     * 基于 openilink SDK 实现与微信的对接，支持扫码登录及消息收发。
     * 使用从 application.yml 读取的 token 构建 WeixinConfig。
     * </p>
     *
     * @param gateway 网关中央控制器
     * @return WeixinChannel 实例
     */
    @Bean
    public WeixinChannel weixinChannel(Gateway gateway) {
        WeixinConfig config = new WeixinConfig();
        config.setToken(weixinToken);
        return new WeixinChannel(config, gateway);
    }

    /**
     * 初始化所有 Vessel 的运行时实例
     * <p>
     * 遍历 VesselManager 中已加载的所有 Vessel 配置，为每个 Vessel 创建独立的 VesselRuntime。
     * VesselRuntime 封装 Spring AI ChatClient，提供独立的 AI 对话能力。
     * 创建完成后将运行时实例注册回 VesselManager，供 AgentLoop 调度使用。
     * </p>
     *
     * @param vesselManager Vessel 管理器，包含已加载的 Vessel 配置
     * @param chatClient    Spring AI ChatClient，底层 AI 模型对话客户端
     */
    public void initializeRuntimes(VesselManager vesselManager, ChatClient chatClient) {
        for (VesselConfig config : vesselManager.listAvailableVessels()) {
            VesselRuntime runtime = new VesselRuntime(config, chatClient);
            vesselManager.registerRuntime(config.getId(), runtime);
        }
    }
}
