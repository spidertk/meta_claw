package meta.claw.core.runtime.subsystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.prompt.PromptVars;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.hitl.*;
import meta.claw.core.tool.SpiToolCall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HITL 子系统。
 * <p>负责工具调用审批策略决策、审批票证生成与审批网关协调。</p>
 */
@Slf4j
@Component
public class HitlSubSystem implements VesselSubSystem {

    @Autowired
    private HitlPolicy hitlPolicy;

    @Autowired
    private HitlGate hitlGate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SubSystemRegistry registry;

    @Override
    public String name() {
        return "hitl";
    }

    @Override
    public void configure(SubSystemRegistry registry) {
        this.registry = registry;
    }

    @Override
    public int priority() {
        return 15;
    }

    @Override
    public PromptVars promptVars() {
        String summary = hitlPolicy.getSummary();
        return (summary == null || summary.isBlank())
                ? PromptVars.empty()
                : PromptVars.of("hitl_policy", summary);
    }

    /**
     * 评估一组工具调用是否需要人工审批。
     */
    public HitlEvaluation evaluate(List<SpiToolCall> toolCalls, TaskContext ctx) {
        List<HitlDecision> decisions = new ArrayList<>();
        List<ApprovalItem> pendingItems = new ArrayList<>();

        for (SpiToolCall tc : toolCalls) {
            ToolCallContext toolCtx = ToolCallContext.builder()
                    .toolName(tc.getName())
                    .arguments(tc.getArguments())
                    .vesselId(ctx.getTask().getVesselId())
                    .taskId(ctx.getTask().getTaskId())
                    .stepNumber(ctx.getSteps().size() + 1)
                    .build();

            HitlDecision decision = hitlPolicy.decide(toolCtx);
            decisions.add(decision);

            if (decision == HitlDecision.REQUIRE_APPROVAL) {
                pendingItems.add(ApprovalItem.builder()
                        .toolCallId(tc.getId())
                        .toolName(tc.getName())
                        .argumentsJson(toJson(tc.getArguments()))
                        .displaySummary(tc.getName() + ": " + tc.getArguments())
                        .build());
            }
        }

        if (!pendingItems.isEmpty()) {
            ApprovalTicket ticket = ApprovalTicket.builder()
                    .ticketId(UUID.randomUUID().toString())
                    .taskId(ctx.getTask().getTaskId())
                    .items(pendingItems)
                    .createdAt(Instant.now())
                    .build();
            return HitlEvaluation.suspended(ticket, decisions);
        }
        return HitlEvaluation.approved(decisions);
    }

    /**
     * 阻塞等待票证决议。
     */
    public ApprovalResolution awaitApproval(ApprovalTicket ticket) {
        return hitlGate.await(ticket);
    }

    /**
     * 外部提交决议。
     */
    public void resolve(String ticketId, ApprovalResolution resolution) {
        hitlGate.resolve(ticketId, resolution);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize tool arguments to JSON", e);
            return String.valueOf(value);
        }
    }
}
