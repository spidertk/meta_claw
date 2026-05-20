package meta.claw.tool.registry;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.spi.llm.SpiToolDefinition;
import meta.claw.tool.annotation.Tool;
import meta.claw.tool.schema.JsonSchemaGenerator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表。
 * 扫描并注册带 {@link Tool} 注解的方法，为 LLM 提供可用工具列表和调用入口。
 */
@Slf4j
public class ToolRegistry {

    private final JsonSchemaGenerator schemaGenerator = new JsonSchemaGenerator();
    private final Map<String, ToolMethod> methods = new ConcurrentHashMap<>();
    private final List<SpiToolDefinition> definitions = new ArrayList<>();

    /**
     * 注册一个工具实例。扫描其所有带 {@link Tool} 注解的方法。
     */
    public void register(Object toolInstance) {
        if (toolInstance == null) {
            return;
        }
        Class<?> clazz = toolInstance.getClass();
        for (Method method : clazz.getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            String toolName = tool.name();
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
            log.info("Registered tool: {} from {}", toolName, clazz.getName());
        }
    }

    public List<SpiToolDefinition> getToolDefinitions() {
        return Collections.unmodifiableList(definitions);
    }

    public ToolMethod findMethod(String toolName) {
        return methods.get(toolName);
    }

    public boolean hasTool(String toolName) {
        return methods.containsKey(toolName);
    }

    public record ToolMethod(Object target, Method method) {
    }
}
