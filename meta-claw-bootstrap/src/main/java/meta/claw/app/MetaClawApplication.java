package meta.claw.app;

import meta.claw.core.config.GlobalConfigLoader;
import meta.claw.vessel.ProjectRootFinder;
import meta.claw.core.config.GlobalConfig;
import meta.claw.gateway.Gateway;
import meta.claw.gateway.weixin.WeixinChannel;
import meta.claw.core.runtime.AgentLoop;
import meta.claw.core.runtime.VesselManager;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Meta-Claw 系统启动类
 * <p>
 * Spring Boot 应用入口，扫描 meta.claw 包及其子包下的所有组件。
 * 同时实现 CommandLineRunner 接口，在 Spring 容器启动完成后按既定顺序执行系统初始化：
 * 1. 为每个 Vessel 创建运行时实例；
 * 2. 注册并启动微信渠道；
 * 3. 启动 AgentLoop 事件监听循环。
 * </p>
 */
@SpringBootApplication(scanBasePackages = "meta.claw")
public class MetaClawApplication implements CommandLineRunner {

    /**
     * 核心配置类，提供 initializeRuntimes 等初始化方法
     */
    @Autowired
    private AppConfig appConfig;

    /**
     * Vessel 管理器，维护所有 Vessel 配置及运行时实例
     */
    @Autowired
    private VesselManager vesselManager;

    /**
     * Spring AI ChatClient.Builder，由 ChatClientAutoConfiguration 自动配置注入
     */
    @Autowired
    private ChatClient.Builder chatClientBuilder;

    /**
     * 网关中央控制器，负责渠道注册与消息路由
     */
    @Autowired
    private Gateway gateway;

    /**
     * 微信渠道实例，用于与微信生态对接
     */
    @Autowired
    private WeixinChannel weixinChannel;

    /**
     * Agent 事件循环处理器，负责调度 Vessel 处理用户消息
     */
    @Autowired
    private AgentLoop agentLoop;

    /**
     * 应用程序主入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(MetaClawApplication.class);

        app.addListeners((ApplicationListener<ApplicationEnvironmentPreparedEvent>) event -> {
            Path configDir = ProjectRootFinder.getMetaClawDir();
            GlobalConfigLoader loader = new GlobalConfigLoader();
            GlobalConfig config = loader.load(configDir);

            LoggingSystem loggingSystem = LoggingSystem.get(MetaClawApplication.class.getClassLoader());
            if (config != null && Boolean.TRUE.equals(config.getLogDebug())) {
                loggingSystem.setLogLevel("meta.claw", LogLevel.DEBUG);
            } else {
                loggingSystem.setLogLevel("meta.claw", LogLevel.INFO);
            }
        });

        app.run(args);
    }

    /**
     * Spring Boot 启动完成后执行的初始化逻辑
     * <p>
     * 严格按照以下顺序执行，确保各组件依赖关系正确：
     * <ol>
     *   <li>initializeRuntimes：为每个已加载的 Vessel 创建 VesselRuntime 并注册；</li>
     *   <li>registerChannel(weixin)：将微信渠道注册到 Gateway 并启动监听；</li>
     *   <li>agentLoop.start()：启动 AgentLoop，开始处理用户消息事件。</li>
     * </ol>
     * </p>
     *
     * @param args 命令行参数
     */
    @Override
    public void run(String... args) {
        // 步骤 1：为所有已加载的 Vessel 创建运行时实例
        ChatClient chatClient = chatClientBuilder.build();
        appConfig.initializeRuntimes(vesselManager, chatClient);

        // 步骤 2：注册并启动微信渠道
        gateway.registerChannel(weixinChannel);

        // 步骤 3：启动 AgentLoop 事件处理循环
        agentLoop.start();
    }
}
