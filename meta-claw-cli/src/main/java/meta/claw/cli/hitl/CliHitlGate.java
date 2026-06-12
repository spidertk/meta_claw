package meta.claw.cli.hitl;

import meta.claw.core.runtime.hitl.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * CLI 场景下的同步 HITL 网关。
 * <p>直接在控制台打印待审批工具并读取用户输入。</p>
 */
@Component
@ConditionalOnProperty(name = "meta.claw.channel", havingValue = "cli", matchIfMissing = true)
public class CliHitlGate implements HitlGate {

    @Override
    public ApprovalResolution await(ApprovalTicket ticket) {
        System.out.println("\n🔒 以下工具调用需要审批：");
        for (ApprovalItem item : ticket.getItems()) {
            System.out.println("  - " + item.getToolName() + ": " + item.getArgumentsJson());
        }
        System.out.print("批准全部? (Y/n): ");
        String input = new Scanner(System.in).nextLine().trim();
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
