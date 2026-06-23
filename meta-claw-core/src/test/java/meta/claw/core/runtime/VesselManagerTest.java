package meta.claw.core.runtime;

import meta.claw.core.config.HitlConfig;
import meta.claw.core.config.VesselConfig;
import meta.claw.core.runtime.hitl.ConfigurableHitlPolicy;
import meta.claw.core.runtime.hitl.HitlDecision;
import meta.claw.core.runtime.hitl.ToolCallContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VesselManagerTest {

    @Test
    void configureHitlPolicyAppliesPerVesselConfig() {
        VesselManager manager = new VesselManager();
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        ReflectionTestUtils.setField(manager, "hitlPolicy", policy);

        HitlConfig hitlConfig = new HitlConfig();
        hitlConfig.setRequire(List.of("execute"));
        hitlConfig.setSkip(List.of("readFile"));
        hitlConfig.setDefaultRequireApproval(false);

        VesselConfig vesselConfig = new VesselConfig();
        vesselConfig.getIdentity().setId("v1");
        vesselConfig.setHitl(hitlConfig);

        ReflectionTestUtils.invokeMethod(manager, "configureHitlPolicy", vesselConfig);

        assertEquals(HitlDecision.REQUIRE_APPROVAL,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("execute").build()));
        assertEquals(HitlDecision.APPROVE_AUTO,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("readFile").build()));
        assertEquals(HitlDecision.APPROVE_AUTO,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("calculate").build()));
    }

    @Test
    void configureHitlPolicyInheritsGlobalDefaultsForUnsetFields() {
        VesselManager manager = new VesselManager();
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        policy.configure(Set.of("execute"), Set.of("readFile"));
        ReflectionTestUtils.setField(manager, "hitlPolicy", policy);

        HitlConfig hitlConfig = new HitlConfig();
        hitlConfig.setDefaultRequireApproval(null);
        hitlConfig.setRequire(null);
        hitlConfig.setSkip(null);

        VesselConfig vesselConfig = new VesselConfig();
        vesselConfig.getIdentity().setId("v1");
        vesselConfig.setHitl(hitlConfig);

        ReflectionTestUtils.invokeMethod(manager, "configureHitlPolicy", vesselConfig);

        assertEquals(HitlDecision.REQUIRE_APPROVAL,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("execute").build()));
        assertEquals(HitlDecision.APPROVE_AUTO,
                policy.decide(ToolCallContext.builder().vesselId("v1").toolName("readFile").build()));
    }
}
