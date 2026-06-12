package meta.claw.core.runtime.hitl;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * 用户对审批票证的决议结果。
 */
@Builder
@Getter
public class ApprovalResolution {
    private final String ticketId;
    private final Map<String, ApprovalStatus> decisions;
    private final String operator;
    private final Instant resolvedAt;
}
