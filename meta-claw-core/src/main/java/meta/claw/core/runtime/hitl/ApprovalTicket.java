package meta.claw.core.runtime.hitl;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * HITL 挂起时生成的审批票证。
 */
@Builder
@Getter
public class ApprovalTicket {
    private final String ticketId;
    private final String taskId;
    private final List<ApprovalItem> items;
    private final Instant createdAt;
}
