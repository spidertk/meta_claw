package meta.claw.core.runtime.hitl;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

/**
 * HITL 对一组工具调用的评估结果。
 */
@Getter
public class HitlEvaluation {
    private final List<HitlDecision> decisions;
    private final ApprovalTicket ticket;

    private HitlEvaluation(List<HitlDecision> decisions, ApprovalTicket ticket) {
        this.decisions = decisions;
        this.ticket = ticket;
    }

    public static HitlEvaluation approved(List<HitlDecision> decisions) {
        return new HitlEvaluation(decisions, null);
    }

    public static HitlEvaluation suspended(ApprovalTicket ticket, List<HitlDecision> decisions) {
        return new HitlEvaluation(decisions, ticket);
    }

    public boolean hasSuspensions() {
        return ticket != null;
    }

    public List<HitlDecision> getDecisions() {
        return Collections.unmodifiableList(decisions);
    }
}
