package meta.claw.core.tool.executor;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.tool.SpiToolCall;
import meta.claw.core.tool.SpiToolResult;
import meta.claw.core.tool.annotation.ToolParam;
import meta.claw.core.tool.registry.ToolRegistry;

import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 工具执行器。
 * 通过反射调用已注册的工具方法，支持超时控制和异常隔离。
 */
@Slf4j
public class ToolExecutor {

    private static final long DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * 执行单次工具调用。
     *
     * @param call    LLM 返回的工具调用描述
     * @param method  注册表中找到的目标方法封装
     * @return 工具执行结果
     */
    public SpiToolResult execute(SpiToolCall call, ToolRegistry.ToolMethod method) {
        if (call == null || method == null) {
            return SpiToolResult.builder()
                    .toolCallId(call != null ? call.id() : null)
                    .success(false)
                    .errorMessage("Missing tool call or method")
                    .build();
        }
        try {
            Object[] args = buildArguments(call, method);
            Object result = invokeWithTimeout(method, args);
            return SpiToolResult.builder()
                    .toolCallId(call.id())
                    .success(true)
                    .content(result != null ? result.toString() : "")
                    .build();
        } catch (Exception e) {
            log.error("Tool execution failed: {} - {}", call.name(), e.getMessage(), e);
            return SpiToolResult.builder()
                    .toolCallId(call.id())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    private Object[] buildArguments(SpiToolCall call, ToolRegistry.ToolMethod method) {
        Parameter[] params = method.method().getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            ToolParam toolParam = params[i].getAnnotation(ToolParam.class);
            if (toolParam == null) {
                continue;
            }
            String paramName = toolParam.name().isEmpty() ? params[i].getName() : toolParam.name();
            Object value = call.arguments() != null ? call.arguments().get(paramName) : null;
            args[i] = convertValue(value, params[i].getType());
        }
        return args;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType == String.class) {
            return value.toString();
        }
        if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(value.toString());
        }
        if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(value.toString());
        }
        if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(value.toString());
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value.toString());
        }
        return value;
    }

    private Object invokeWithTimeout(ToolRegistry.ToolMethod method, Object[] args) throws Exception {
        CompletableFuture<Object> future = CompletableFuture.supplyAsync(() -> {
            try {
                method.method().setAccessible(true);
                return method.method().invoke(method.target(), args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return future.get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
