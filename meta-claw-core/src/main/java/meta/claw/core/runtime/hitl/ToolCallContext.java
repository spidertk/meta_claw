package meta.claw.core.runtime.hitl;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * 工具调用上下文，供 HITL 策略做审批决策。
 */
@Builder
@Getter
public class ToolCallContext {
    private final String toolName;
    private final Map<String, Object> arguments;
    private final String vesselId;
    private final String taskId;
    private final int stepNumber;
}
