package meta.claw.core.runtime.hitl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConfigurableHitlPolicyTest {

    @Test
    void defaultApprovesAll() {
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        ToolCallContext ctx = ToolCallContext.builder().toolName("calculator").build();
        assertEquals(HitlDecision.APPROVE_AUTO, policy.decide(ctx));
    }

    @Test
    void requireSetForcesApproval() {
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        policy.configure(Set.of("dangerous"), Set.of());
        ToolCallContext ctx = ToolCallContext.builder().toolName("dangerous").build();
        assertEquals(HitlDecision.REQUIRE_APPROVAL, policy.decide(ctx));
    }

    @Test
    void skipSetOverridesRequire() {
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        policy.configure(Set.of("dangerous"), Set.of("safe"));
        assertEquals(HitlDecision.APPROVE_AUTO, policy.decide(ToolCallContext.builder().toolName("safe").build()));
        assertEquals(HitlDecision.REQUIRE_APPROVAL, policy.decide(ToolCallContext.builder().toolName("dangerous").build()));
    }

    @Test
    void globalDefaultRequireApprovalForcesAll() {
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        ReflectionTestUtils.setField(policy, "globalDefaultRequireApproval", true);
        assertEquals(HitlDecision.REQUIRE_APPROVAL, policy.decide(ToolCallContext.builder().toolName("any").build()));
    }

    @Test
    void perVesselConfigIsolated() {
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        policy.configure("v1", Set.of("writeFile"), Set.of(), false);
        policy.configure("v2", Set.of(), Set.of("execute"), false);

        assertEquals(HitlDecision.REQUIRE_APPROVAL,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("writeFile").build()));
        assertEquals(HitlDecision.APPROVE_AUTO,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("execute").build()));

        assertEquals(HitlDecision.APPROVE_AUTO,
                policy.decide(ToolCallContext.builder().vesselId("v2").toolName("writeFile").build()));
        assertEquals(HitlDecision.APPROVE_AUTO,
                policy.decide(ToolCallContext.builder().vesselId("v2").toolName("execute").build()));
    }

    @Test
    void perVesselDefaultOverridesGlobal() {
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        ReflectionTestUtils.setField(policy, "globalDefaultRequireApproval", true);
        policy.configure("v1", Set.of(), Set.of(), false);

        assertEquals(HitlDecision.APPROVE_AUTO,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("any").build()));
        assertEquals(HitlDecision.REQUIRE_APPROVAL,
                policy.decide(ToolCallContext.builder().vesselId("v2").toolName("any").build()));
    }

    @Test
    void skipOverridesPerVesselDefault() {
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        policy.configure("v1", Set.of(), Set.of("safe"), true);

        assertEquals(HitlDecision.APPROVE_AUTO,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("safe").build()));
        assertEquals(HitlDecision.REQUIRE_APPROVAL,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("dangerous").build()));
    }

    @Test
    void perVesselConfigInheritsUnsetFieldsFromGlobal() {
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        policy.configure(Set.of("execute", "writeFile"), Set.of("readFile"));
        // v1 只覆盖 defaultRequireApproval，require/skip 继承全局
        policy.configure("v1", null, null, true);

        assertEquals(HitlDecision.REQUIRE_APPROVAL,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("execute").build()));
        assertEquals(HitlDecision.REQUIRE_APPROVAL,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("unknown").build()));
        assertEquals(HitlDecision.APPROVE_AUTO,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("readFile").build()));

        // v2 没有 vessel 配置，完全使用全局
        assertEquals(HitlDecision.REQUIRE_APPROVAL,
                policy.decide(ToolCallContext.builder().vesselId("v2").toolName("execute").build()));
        assertEquals(HitlDecision.APPROVE_AUTO,
                policy.decide(ToolCallContext.builder().vesselId("v2").toolName("unknown").build()));
    }

    @Test
    void nullRequireAndSkipDoNotCauseNpe() {
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        // 全局只设置 defaultRequireApproval=true，require/skip 为 null
        policy.configure(null, (Set<String>) null, (Set<String>) null, true);

        assertEquals(HitlDecision.REQUIRE_APPROVAL,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("any").build()));
        assertEquals("所有工具调用都需要人工审批。", policy.getSummary());
    }

    @Test
    void perVesselConfigCanClearGlobalRequireWithEmptySet() {
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        policy.configure(Set.of("execute"), Set.of());
        // v1 显式设置 require 为空，覆盖全局的 execute
        policy.configure("v1", Set.of(), null, null);

        assertEquals(HitlDecision.APPROVE_AUTO,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("execute").build()));
        assertEquals(HitlDecision.REQUIRE_APPROVAL,
                policy.decide(ToolCallContext.builder().vesselId("v2").toolName("execute").build()));
    }
}
