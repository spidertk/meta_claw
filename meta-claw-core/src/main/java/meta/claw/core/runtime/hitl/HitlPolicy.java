package meta.claw.core.runtime.hitl;

/**
 * HITL 决策策略。
 */
public interface HitlPolicy {

    /**
     * 根据工具调用上下文决定是否需要人工审批。
     */
    HitlDecision decide(ToolCallContext context);

    /**
     * 返回策略摘要，用于注入系统提示。
     */
    default String getSummary() {
        return null;
    }
}
