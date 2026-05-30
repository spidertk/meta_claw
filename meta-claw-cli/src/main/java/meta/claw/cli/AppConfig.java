package meta.claw.cli;

import meta.claw.core.config.VesselConfig;
import meta.claw.core.config.VesselConfigLoader;
import meta.claw.core.eventbus.EventBusWrapper;
import meta.claw.core.memory.shortterm.ShortMemory;
import meta.claw.core.runtime.AgentLoop;
import meta.claw.core.runtime.VesselManager;
import meta.claw.core.runtime.VesselRuntime;
import meta.claw.core.util.ProjectRootFinder;
import org.springframework.beans.factory.ObjectProvider;
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
    public VesselManager vesselManager(VesselConfigLoader vesselConfigLoader) {
        VesselManager manager = new VesselManager(ProjectRootFinder.getMetaClawDir(), vesselConfigLoader);
        manager.loadVessels();
        return manager;
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
     */
    public void initializeRuntimes(VesselManager vesselManager,
                                   ObjectProvider<VesselRuntime> runtimes) {
        for (VesselConfig config : vesselManager.listAvailableVessels()) {
            VesselRuntime runtime = runtimes.getObject(config.getName());
            vesselManager.registerRuntime(config.getId(), runtime);
        }
    }
}
