package meta.claw.core.tool.schema;

import meta.claw.core.llm.SpiJsonSchema;
import meta.claw.core.tool.annotation.Tool;
import meta.claw.core.tool.annotation.ToolParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 从带 {@link Tool} 注解的方法生成 JSON Schema。
 */
public class JsonSchemaGenerator {

    public SpiJsonSchema generate(Method method) {
        Map<String, SpiJsonSchema> properties = new LinkedHashMap<>();
        Parameter[] params = method.getParameters();
        for (Parameter param : params) {
            ToolParam toolParam = param.getAnnotation(ToolParam.class);
            if (toolParam == null) {
                continue;
            }
            String type = mapJavaTypeToJsonType(param.getType());
            String paramName = toolParam.name().isEmpty() ? param.getName() : toolParam.name();
            properties.put(paramName, SpiJsonSchema.builder()
                    .type(type)
                    .description(toolParam.description())
                    .build());
        }
        return SpiJsonSchema.builder()
                .type("object")
                .description("")
                .properties(properties)
                .build();
    }

    private String mapJavaTypeToJsonType(Class<?> type) {
        if (type == String.class) {
            return "string";
        }
        if (type == int.class || type == Integer.class
                || type == long.class || type == Long.class) {
            return "integer";
        }
        if (type == double.class || type == Double.class
                || type == float.class || type == Float.class) {
            return "number";
        }
        if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        }
        return "string";
    }
}
