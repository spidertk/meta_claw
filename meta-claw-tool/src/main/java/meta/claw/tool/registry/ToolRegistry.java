package meta.claw.tool.registry;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.spi.llm.SpiToolDefinition;
import meta.claw.tool.annotation.Tool;
import meta.claw.tool.schema.JsonSchemaGenerator;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工具注册表。
 * 扫描并注册带 {@link Tool} 注解的方法，为 LLM 提供可用工具列表和调用入口。
 * <p>
 * 作为 Spring 组件管理，启动时自动扫描容器中所有 bean 的 {@link Tool} 方法。
 * 运行时支持通过 {@link #register(Object)} 动态热注入新工具，
 * 以及通过 {@link #unregister(String)} 卸载工具，为组件热进化提供基础能力。
 * </p>
 */
@Slf4j
@Component
public class ToolRegistry {

    private final JsonSchemaGenerator schemaGenerator = new JsonSchemaGenerator();
    private final Map<String, ToolMethod> methods = new ConcurrentHashMap<>();
    private final List<SpiToolDefinition> definitions = new CopyOnWriteArrayList<>();
    private final ApplicationContext applicationContext;

    public ToolRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Spring 初始化后自动扫描所有 bean 中标记了 {@link Tool} 的方法。
     */
    @PostConstruct
    public void scanAndRegisterBeans() {
        Map<String, Object> toolBeans = applicationContext.getBeansWithAnnotation(Tool.class);
        // getBeansWithAnnotation 在类级别查找，但 @Tool 是方法级别注解，
        // 所以改用遍历所有 bean 实例并反射扫描方法
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                continue;
            }
            register(bean);
        }
        log.info("ToolRegistry 初始化完成，共注册 {} 个工具", definitions.size());
    }

    /**
     * 注册一个工具实例。扫描其所有带 {@link Tool} 注解的方法。
     * 支持运行时动态热注入。
     */
    public void register(Object toolInstance) {
        if (toolInstance == null) {
            return;
        }
        Class<?> clazz = toolInstance.getClass();
        boolean anyRegistered = false;
        for (Method method : clazz.getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            String toolName = tool.name();
            synchronized (this) {
                if (methods.containsKey(toolName)) {
                    log.warn("Tool '{}' already registered, skipping duplicate from {}", toolName, clazz.getName());
                    continue;
                }
                SpiToolDefinition definition = SpiToolDefinition.builder()
                        .name(toolName)
                        .description(tool.description())
                        .parameters(schemaGenerator.generate(method))
                        .build();
                definitions.add(definition);
                methods.put(toolName, new ToolMethod(toolInstance, method));
            }
            anyRegistered = true;
            log.info("Registered tool: {} from {}", toolName, clazz.getName());
        }
        if (anyRegistered) {
            log.debug("工具实例 {} 已注册到 ToolRegistry", clazz.getName());
        }
    }

    /**
     * 按名称卸载已注册的工具。支持运行时热卸载。
     *
     * @param toolName 工具名称
     * @return true 如果成功卸载，false 如果工具不存在
     */
    public boolean unregister(String toolName) {
        synchronized (this) {
            ToolMethod removed = methods.remove(toolName);
            if (removed == null) {
                return false;
            }
            definitions.removeIf(d -> d.name().equals(toolName));
            log.info("Unregistered tool: {}", toolName);
            return true;
        }
    }

    /**
     * 按名称重新注册工具（热替换）。先卸载再注册。
     *
     * @param toolInstance 新的工具实例
     * @return true 如果发生了替换
     */
    public boolean reregister(Object toolInstance) {
        if (toolInstance == null) {
            return false;
        }
        boolean anyReplaced = false;
        for (Method method : toolInstance.getClass().getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            synchronized (this) {
                unregister(tool.name());
                register(toolInstance);
            }
            anyReplaced = true;
        }
        return anyReplaced;
    }

    public List<SpiToolDefinition> getToolDefinitions() {
        return Collections.unmodifiableList(new ArrayList<>(definitions));
    }

    public ToolMethod findMethod(String toolName) {
        return methods.get(toolName);
    }

    public boolean hasTool(String toolName) {
        return methods.containsKey(toolName);
    }

    public int toolCount() {
        return methods.size();
    }

    public record ToolMethod(Object target, Method method) {
    }
}
