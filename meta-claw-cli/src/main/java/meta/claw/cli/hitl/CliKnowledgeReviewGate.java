package meta.claw.cli.hitl;

import meta.claw.core.knowledge.review.KnowledgeReviewGate;
import meta.claw.core.knowledge.review.ReviewDecision;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * CLI 场景下的知识提案人审网关（HITL）。
 * <p>知识分析完成后、落库前，在控制台展示提案预览并等待用户确认：
 * Y 确认入库，n 拒绝，直接回车默认挂起（可稍后通过 knowledgeReview 处理）。</p>
 */
@Component
@Primary
@ConditionalOnProperty(name = "meta.claw.channel", havingValue = "cli", matchIfMissing = true)
public class CliKnowledgeReviewGate implements KnowledgeReviewGate {

    @Autowired
    private Terminal terminal;

    @Autowired
    private LineReader lineReader;

    @Override
    public ReviewDecision review(String proposalId, String preview) {
        terminal.writer().println(preview);
        terminal.writer().flush();

        String input = lineReader.readLine("确认入库? (y=入库 / n=拒绝 / 回车=稍后处理): ").trim();
        if (input.equalsIgnoreCase("y") || input.equalsIgnoreCase("yes")) {
            return ReviewDecision.APPROVED;
        }
        if (input.equalsIgnoreCase("n") || input.equalsIgnoreCase("no")) {
            return ReviewDecision.REJECTED;
        }
        return ReviewDecision.PENDING;
    }
}
