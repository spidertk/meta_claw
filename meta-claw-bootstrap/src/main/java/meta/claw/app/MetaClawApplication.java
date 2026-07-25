package meta.claw.app;

import meta.claw.core.config.loader.GlobalConfigLoader;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.config.GlobalConfig;
import meta.claw.core.runtime.AgentLoop;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;

import java.nio.file.Path;

/**
 * Meta-Claw 系统启动类
 * <p>
 * Spring Boot 应用入口，扫描 meta.claw 包及其子包下的所有组件。
 * Vessel 运行时由 VesselManager（Spring 组件）在容器初始化时自动注册；
 * 微信渠道由 WeixinChannelManager 按配置自动创建并注册到 Gateway；
 * 本类仅需在容器启动完成后启动 AgentLoop 事件监听循环。
 * </p>
 */
@SpringBootApplication(scanBasePackages = "meta.claw")
public class MetaClawApplication implements CommandLineRunner {

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
     * Vessel 运行时注册（VesselManager.afterPropertiesSet）与微信渠道启动（WeixinChannelManager）
     * 均由 Spring 容器自动完成，此处仅需启动 AgentLoop 事件处理循环。
     * </p>
     *
     * @param args 命令行参数
     */
    @Override
    public void run(String... args) {
        // 启动 AgentLoop 事件处理循环
        agentLoop.start();
    }
}
