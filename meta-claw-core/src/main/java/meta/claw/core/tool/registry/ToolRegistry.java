package meta.claw.core.tool.registry;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.tool.annotation.ToolService;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工具注册表。
 * 收集标记了 {@link ToolService} 的 Spring Bean，供 LLM 调用时通过 Spring AI 原生能力自动发现和执行。
 * <p>
 * 运行时支持通过 {@link #register(Object)} 动态热注入新工具，
 * 以及通过 {@link #unregister(Object)} 卸载工具实例。
 * </p>
 */
@Slf4j
@Component
public class ToolRegistry {

    private final ApplicationContext applicationContext;
    private final List<Object> toolInstances = new CopyOnWriteArrayList<>();

    public ToolRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Spring 初始化后自动扫描所有带 {@link ToolService} 注解的 Bean。
     */
    @PostConstruct
    public void scanAndRegisterBeans() {
        Map<String, Object> toolBeans = applicationContext.getBeansWithAnnotation(ToolService.class);
        for (Object bean : toolBeans.values()) {
            register(bean);
        }
        log.info("ToolRegistry 初始化完成，共注册 {} 个工具实例", toolInstances.size());
    }

    /**
     * 注册一个工具实例。支持运行时动态热注入。
     */
    public void register(Object toolInstance) {
        if (toolInstance == null) {
            return;
        }
        Class<?> clazz = AopUtils.getTargetClass(toolInstance);
        toolInstances.add(toolInstance);
        log.info("Registered tool instance: {}", clazz.getName());
    }

    /**
     * 卸载已注册的工具实例。支持运行时热卸载。
     *
     * @param toolInstance 工具实例
     * @return true 如果成功卸载
     */
    public boolean unregister(Object toolInstance) {
        boolean removed = toolInstances.remove(toolInstance);
        if (removed) {
            log.info("Unregistered tool instance: {}", AopUtils.getTargetClass(toolInstance).getName());
        }
        return removed;
    }

    /**
     * 获取所有已注册的工具实例。
     */
    public List<Object> getToolInstances() {
        return Collections.unmodifiableList(new ArrayList<>(toolInstances));
    }

    /**
     * 获取已注册的工具实例数量。
     */
    public int toolCount() {
        return toolInstances.size();
    }
}
