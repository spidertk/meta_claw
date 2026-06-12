package meta.claw.core.runtime;

import lombok.Getter;
import meta.claw.core.exception.ErrorCode;
import meta.claw.core.exception.MetaClawException;
import meta.claw.core.runtime.hitl.ApprovalTicket;

/**
 * HITL 挂起异常。
 * <p>当工具调用需要人工审批时，执行器抛出此异常并携带 {@link ApprovalTicket}。</p>
 */
@Getter
public class HitlSuspendedException extends MetaClawException {

    private final ApprovalTicket ticket;

    public HitlSuspendedException(ApprovalTicket ticket) {
        super(ErrorCode.HITL_SUSPENDED, ticket.getTicketId());
        this.ticket = ticket;
    }
}
