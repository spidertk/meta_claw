package meta.claw.core.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记工具方法参数，用于生成 JSON Schema 中的 properties 描述。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {

    /**
     * 参数在 JSON Schema 中的名称。若为空，则使用反射参数名（需要 -parameters 编译选项）。
     */
    String name() default "";

    /**
     * 参数描述，帮助 LLM 理解该参数的含义和取值范围。
     */
    String description();

    /**
     * 是否为必填参数。
     */
    boolean required() default true;
}
