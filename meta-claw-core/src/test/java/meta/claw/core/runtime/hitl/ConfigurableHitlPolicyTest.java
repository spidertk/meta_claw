package meta.claw.core.runtime.hitl;

import org.junit.jupiter.api.Test;

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
    void defaultRequireApprovalForcesAll() {
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        // 通过反射设置 @Value 字段
        org.springframework.test.util.ReflectionTestUtils.setField(policy, "defaultRequireApproval", true);
        assertEquals(HitlDecision.REQUIRE_APPROVAL, policy.decide(ToolCallContext.builder().toolName("any").build()));
    }
}
