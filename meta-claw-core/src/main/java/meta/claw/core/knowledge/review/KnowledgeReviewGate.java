package meta.claw.core.knowledge.review;

/**
 * 知识提案人审网关（HITL）。
 * <p>知识分析完成后、落库之前必经此关口：
 * CLI 通道为同步交互确认；其他通道（gateway/weixin）返回 PENDING 挂起，
 * 由用户后续通过 knowledgeReview 工具异步决议。</p>
 */
@FunctionalInterface
public interface KnowledgeReviewGate {

    /**
     * @param proposalId 待审提案 ID（已持久化到 knowledge/.pending/{proposalId}.json）
     * @param preview    渲染好的提案预览文本（标题/类型/矛盾/置信度/正文摘要）
     * @return 人审决议
     */
    ReviewDecision review(String proposalId, String preview);
}
