package meta.claw.core.runtime.hitl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可配置的 HITL 策略。
 * <p>支持全局默认审批开关，以及按 Vessel + 工具名的 require/skip 名单。</p>
 * <p>全局配置作为默认值；每个 Vessel 可以独立覆盖其中任意字段。未覆盖的字段继承全局配置。</p>
 */
@Component
public class ConfigurableHitlPolicy implements HitlPolicy {

    private static final String GLOBAL_KEY = "";

    @Value("${hitl.default-require-approval:false}")
    private boolean globalDefaultRequireApproval;

    private final ConcurrentHashMap<String, HitlPolicyConfig> configs = new ConcurrentHashMap<>();

    @Override
    public HitlDecision decide(ToolCallContext context) {
        HitlPolicyConfig config = resolveConfig(context.getVesselId());
        String toolName = context.getToolName();
        if (config.skip().contains(toolName)) {
            return HitlDecision.APPROVE_AUTO;
        }
        if (config.require().contains(toolName) || config.defaultRequireApproval()) {
            return HitlDecision.REQUIRE_APPROVAL;
        }
        return HitlDecision.APPROVE_AUTO;
    }

    /**
     * 配置全局 HITL 策略（旧 API，兼容现有代码）。
     */
    public void configure(Set<String> require, Set<String> skip) {
        configure(GLOBAL_KEY, require, skip, null);
    }

    /**
     * 按 Vessel 配置 HITL 策略。传入 {@code null} 的字段表示继承全局配置。
     *
     * @param vesselId                Vessel 唯一标识
     * @param require                 需要审批的工具名集合，null 表示继承全局
     * @param skip                    自动跳过的工具名集合，null 表示继承全局
     * @param defaultRequireApproval  该 Vessel 的默认审批开关，null 表示继承全局 Spring 属性
     */
    public void configure(String vesselId, Set<String> require, Set<String> skip, Boolean defaultRequireApproval) {
        String key = vesselId != null ? vesselId : GLOBAL_KEY;
        Boolean effectiveDefault = defaultRequireApproval;
        if (effectiveDefault == null && GLOBAL_KEY.equals(key)) {
            effectiveDefault = globalDefaultRequireApproval;
        }
        configs.put(key, new HitlPolicyConfig(effectiveDefault, copy(require), copy(skip)));
    }

    @Override
    public String getSummary() {
        HitlPolicyConfig global = configs.get(GLOBAL_KEY);
        if (global != null && Boolean.TRUE.equals(global.defaultRequireApproval())) {
            return "所有工具调用都需要人工审批。";
        }
        if (global != null && !global.require().isEmpty()) {
            return "需要审批的工具: " + String.join(", ", global.require());
        }
        return null;
    }

    private HitlPolicyConfig resolveConfig(String vesselId) {
        HitlPolicyConfig global = configs.getOrDefault(GLOBAL_KEY, defaultConfig());
        if (vesselId == null || vesselId.isBlank()) {
            return global;
        }
        HitlPolicyConfig vessel = configs.get(vesselId);
        if (vessel == null) {
            return global;
        }
        boolean effectiveDefault = vessel.defaultRequireApproval() != null
                ? vessel.defaultRequireApproval()
                : (global.defaultRequireApproval() != null ? global.defaultRequireApproval() : globalDefaultRequireApproval);
        return new HitlPolicyConfig(
                effectiveDefault,
                vessel.require() != null ? vessel.require() : global.require(),
                vessel.skip() != null ? vessel.skip() : global.skip());
    }

    private HitlPolicyConfig defaultConfig() {
        return new HitlPolicyConfig(globalDefaultRequireApproval, Collections.emptySet(), Collections.emptySet());
    }

    private Set<String> copy(Set<String> source) {
        return source != null ? Set.copyOf(source) : null;
    }

    private record HitlPolicyConfig(Boolean defaultRequireApproval, Set<String> require, Set<String> skip) {}
}
