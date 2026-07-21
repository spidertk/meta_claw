package meta.claw.core.knowledge.review;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 默认人审网关：一律挂起（PENDING），不阻塞调用方。
 * <p>适用于 gateway/weixin 等无法同步交互的通道——提案持久化后返回
 * proposalId，用户后续用 knowledgeReview 工具确认或拒绝。
 * CLI 通道由 CliKnowledgeReviewGate（@Primary）覆盖。</p>
 */
@Component
@ConditionalOnMissingBean(KnowledgeReviewGate.class)
public class PendingKnowledgeReviewGate implements KnowledgeReviewGate {

    @Override
    public ReviewDecision review(String proposalId, String preview) {
        return ReviewDecision.PENDING;
    }
}
