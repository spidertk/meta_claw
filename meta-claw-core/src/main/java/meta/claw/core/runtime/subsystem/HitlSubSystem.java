package meta.claw.core.runtime.subsystem;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.GlobalConfig;
import meta.claw.core.config.HitlConfig;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.config.loader.GlobalConfigLoader;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.prompt.PromptVars;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselManager;
import meta.claw.core.runtime.hitl.*;
import meta.claw.core.tool.SpiToolCall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HITL 子系统。
 * <p>负责工具调用审批策略决策、审批票证生成与审批网关协调。</p>
 */
@Slf4j
@Component
public class HitlSubSystem implements VesselSubSystem, VesselAwareSubSystem {

    @Autowired
    private HitlPolicy hitlPolicy;

    @Autowired
    private HitlGate hitlGate;

    @Autowired
    private VesselManager vesselManager;

    @Autowired
    private GlobalConfigLoader globalConfigLoader;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SubSystemRegistry registry;
    private boolean globalHitlLoaded = false;

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

    @PostConstruct
    public void loadGlobalHitlConfig() {
        if (globalHitlLoaded) {
            return;
        }
        if (globalConfigLoader == null) {
            log.debug("GlobalConfigLoader not available, skipping global HITL configuration");
            return;
        }
        Path baseDir = ProjectRootFinder.getMetaClawDir();
        GlobalConfig globalConfig = globalConfigLoader.load(baseDir);
        if (globalConfig == null || globalConfig.getHitl() == null) {
            log.debug("No global HITL config found");
            return;
        }
        HitlConfig hitl = globalConfig.getHitl();
        if (hitlPolicy instanceof ConfigurableHitlPolicy configurable) {
            Set<String> require = hitl.getRequire() != null ? Set.copyOf(hitl.getRequire()) : null;
            Set<String> skip = hitl.getSkip() != null ? Set.copyOf(hitl.getSkip()) : null;
            configurable.configure(null, require, skip, hitl.getDefaultRequireApproval());
            log.info("Loaded global HITL config: require={}, skip={}, defaultRequireApproval={}",
                    require, skip, hitl.getDefaultRequireApproval());
        } else {
            log.warn("HITL policy is not configurable, cannot apply global HITL settings");
        }
        globalHitlLoaded = true;
    }

    @Override
    public void loadForVessel(String vesselId) {
        if (vesselManager == null) {
            log.warn("VesselManager not available, skipping per-vessel HITL configuration");
            return;
        }
        VesselConfig config = vesselManager.getConfig(vesselId);
        if (config == null || config.getHitl() == null) {
            log.debug("No HITL config found for vessel {}", vesselId);
            return;
        }
        HitlConfig hitlConfig = config.getHitl();
        Set<String> require = hitlConfig.getRequire() != null
                ? Set.copyOf(hitlConfig.getRequire())
                : null;
        Set<String> skip = hitlConfig.getSkip() != null
                ? Set.copyOf(hitlConfig.getSkip())
                : null;
        if (hitlPolicy instanceof ConfigurableHitlPolicy configurable) {
            configurable.configure(vesselId, require, skip, hitlConfig.getDefaultRequireApproval());
            log.info("Loaded HITL config for vessel {}: require={}, skip={}, defaultRequireApproval={}",
                    vesselId, require, skip, hitlConfig.getDefaultRequireApproval());
        } else {
            log.warn("HITL policy is not configurable, cannot apply per-vessel HITL settings");
        }
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
    public ApprovalResolution awaitResolution(ApprovalTicket ticket) {
        return hitlGate.await(ticket);
    }

    private String toJson(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize tool arguments: {}", arguments, e);
            return arguments != null ? arguments.toString() : "";
        }
    }
}
