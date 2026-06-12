package meta.claw.core.runtime.hitl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可配置的 HITL 策略。
 * <p>支持全局默认审批开关，以及按工具名的 require/skip 名单。</p>
 */
@Component
public class ConfigurableHitlPolicy implements HitlPolicy {

    @Value("${hitl.default-require-approval:false}")
    private boolean defaultRequireApproval;

    private final Set<String> requireApprovalSet = ConcurrentHashMap.newKeySet();
    private final Set<String> skipApprovalSet = ConcurrentHashMap.newKeySet();

    @Override
    public HitlDecision decide(ToolCallContext context) {
        if (skipApprovalSet.contains(context.getToolName())) {
            return HitlDecision.APPROVE_AUTO;
        }
        if (requireApprovalSet.contains(context.getToolName()) || defaultRequireApproval) {
            return HitlDecision.REQUIRE_APPROVAL;
        }
        return HitlDecision.APPROVE_AUTO;
    }

    /**
     * 运行时配置审批名单。
     */
    public void configure(Set<String> require, Set<String> skip) {
        requireApprovalSet.addAll(require);
        skipApprovalSet.addAll(skip);
    }

    @Override
    public String getSummary() {
        if (defaultRequireApproval) {
            return "所有工具调用都需要人工审批。";
        }
        if (requireApprovalSet.isEmpty()) {
            return null;
        }
        return "需要审批的工具: " + String.join(", ", requireApprovalSet);
    }
}
