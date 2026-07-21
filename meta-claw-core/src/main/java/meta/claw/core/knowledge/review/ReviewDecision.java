package meta.claw.core.knowledge.review;

/**
 * 知识提案的人审决议。
 */
public enum ReviewDecision {
    /** 确认入库：立即写盘并 git commit。 */
    APPROVED,
    /** 拒绝：丢弃提案，不写盘。 */
    REJECTED,
    /** 挂起：提案保留在 .pending/ 中，等待后续通过 knowledgeReview 工具确认。 */
    PENDING
}
