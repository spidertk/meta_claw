package meta.claw.core.tool.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为工具服务，自动注册为 Spring Bean。
 * <p>
 * 工具方法应使用 Spring AI 原生的 {@link org.springframework.ai.tool.annotation.Tool}
 * 注解标记，框架会自动发现并注册。
 * </p>
 *
 * <pre>{@code
 * @ToolService
 * public class CalculatorTool {
 *     @org.springframework.ai.tool.annotation.Tool(description = "计算数学表达式")
 *     public String calculate(String expression) { ... }
 * }
 * }</pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface ToolService {
}
