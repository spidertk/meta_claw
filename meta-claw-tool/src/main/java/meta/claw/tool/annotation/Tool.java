package meta.claw.tool.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为可供 LLM 调用的工具。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {

    /**
     * 工具名称，LLM 通过该名称识别并调用工具。
     */
    String name();

    /**
     * 工具功能描述，注入 system prompt 供 LLM 理解用途。
     */
    String description();

    /**
     * 是否需要用户审批后才执行。
     */
    boolean approvalRequired() default false;
}
