package meta.claw.cli.hitl;

import meta.claw.core.runtime.hitl.*;
import org.jline.reader.LineReader;
import org.jline.terminal.Terminal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * CLI 场景下的同步 HITL 网关。
 * <p>直接在控制台打印待审批工具并通过共享的 {@link LineReader} 读取用户输入。</p>
 */
@Component
@ConditionalOnProperty(name = "meta.claw.channel", havingValue = "cli", matchIfMissing = true)
public class CliHitlGate implements HitlGate {

    @Autowired
    private Terminal terminal;

    @Autowired
    private LineReader lineReader;

    @Override
    public ApprovalResolution await(ApprovalTicket ticket) {
        terminal.writer().println("\n🔒 以下工具调用需要审批：");
        for (ApprovalItem item : ticket.getItems()) {
            terminal.writer().printf("  - %s: %s%n", item.getToolName(), item.getArgumentsJson());
        }
        terminal.writer().flush();

        String input = lineReader.readLine("批准全部? (Y/n): ").trim();
        boolean approved = input.isEmpty()
                || input.equalsIgnoreCase("Y")
                || input.equalsIgnoreCase("yes");

        Map<String, ApprovalStatus> decisions = new HashMap<>();
        for (ApprovalItem item : ticket.getItems()) {
            decisions.put(item.getToolCallId(), approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        }
        return ApprovalResolution.builder()
                .ticketId(ticket.getTicketId())
                .decisions(decisions)
                .operator("cli-user")
                .build();
    }

    @Override
    public void resolve(String ticketId, ApprovalResolution resolution) {
        // CLI 同步场景不依赖外部 resolve
    }
}
