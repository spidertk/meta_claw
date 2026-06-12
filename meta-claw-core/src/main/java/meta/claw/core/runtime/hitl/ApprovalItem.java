package meta.claw.core.runtime.hitl;

import lombok.Builder;
import lombok.Getter;

/**
 * 审批票证中的单个待审批项。
 */
@Builder
@Getter
public class ApprovalItem {
    private final String toolCallId;
    private final String toolName;
    private final String argumentsJson;
    private final String displaySummary;
}
