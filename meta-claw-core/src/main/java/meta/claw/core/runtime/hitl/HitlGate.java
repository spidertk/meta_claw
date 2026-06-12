package meta.claw.core.runtime.hitl;

/**
 * HITL 审批网关。
 * <p>负责等待人工决议，以及接收外部决议通知。</p>
 */
public interface HitlGate {

    /**
     * 阻塞等待用户对指定票证的决议。
     */
    ApprovalResolution await(ApprovalTicket ticket);

    /**
     * 外部提交决议通知。
     */
    void resolve(String ticketId, ApprovalResolution resolution);
}
