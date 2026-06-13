package meta.claw.core.tool.registry;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.tool.annotation.ToolService;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.ai.tool.annotation.Tool;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工具注册表。
 * <p>收集以下两类工具实例供 LLM 调用：</p>
 * <ul>
 *   <li>标记了 {@link ToolService} 的本地工具 Bean</li>
 *   <li>类或方法上带有 Spring AI 原生 {@link Tool} 注解的工具 Bean（如 Spring AI Alibaba 工具集）</li>
 * </ul>
 * <p>运行时支持通过 {@link #register(Object)} 动态热注入新工具，
 * 以及通过 {@link #unregister(Object)} 卸载工具实例。</p>
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
     * Spring 初始化后自动扫描所有工具 Bean。
     */
    @PostConstruct
    public void scanAndRegisterBeans() {
        // 1. 扫描自定义 @ToolService
        Map<String, Object> toolServiceBeans = applicationContext.getBeansWithAnnotation(ToolService.class);
        for (Object bean : toolServiceBeans.values()) {
            register(bean);
        }

        // 2. 扫描 Spring AI 原生 @Tool（方法级注解），覆盖 Alibaba 等外部工具集
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                log.debug("Skipping bean {} during tool scan: {}", beanName, e.getMessage());
                continue;
            }
            if (toolServiceBeans.containsValue(bean)) {
                continue; // 已注册，避免重复
            }
            Class<?> clazz = AopUtils.getTargetClass(bean);
            if (hasToolAnnotation(clazz)) {
                register(bean);
            }
        }

        log.info("ToolRegistry 初始化完成，共注册 {} 个工具实例", toolInstances.size());
    }

    private boolean hasToolAnnotation(Class<?> clazz) {
        if (clazz.isAnnotationPresent(Tool.class)) {
            return true;
        }
        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Tool.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 注册一个工具实例。支持运行时动态热注入。
     */
    public void register(Object toolInstance) {
        if (toolInstance == null) {
            return;
        }
        Class<?> clazz = AopUtils.getTargetClass(toolInstance);
        if (!toolInstances.contains(toolInstance)) {
            toolInstances.add(toolInstance);
            log.info("Registered tool instance: {}", clazz.getName());
        }
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
