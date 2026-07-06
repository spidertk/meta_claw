package meta.claw.core.runtime.hitl;

import meta.claw.core.config.GlobalConfig;
import meta.claw.core.config.HitlConfig;
import meta.claw.core.config.loader.GlobalConfigLoader;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.subsystem.HitlSubSystem;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.tool.SpiToolCall;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HitlSubSystemTest {

    @Test
    void evaluateApprovesWhenPolicyAutoApproves() {
        HitlSubSystem hitl = new HitlSubSystem();
        hitl.configure(new SubSystemRegistry());
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        ReflectionTestUtils.setField(hitl, "hitlPolicy", policy);
        ReflectionTestUtils.setField(hitl, "hitlGate", new InMemoryHitlGate());

        TaskContext ctx = TaskContext.builder()
                .taskId("t1")
                .vesselId("v1")
                .profile(null)
                .registry(new SubSystemRegistry())
                .build();
        SpiToolCall tc = SpiToolCall.builder().id("c1").name("calculator").arguments(Map.of("a", 1)).build();
        HitlEvaluation eval = hitl.evaluate(List.of(tc), ctx);
        assertFalse(eval.hasSuspensions());
    }

    @Test
    void evaluateSuspendsWhenPolicyRequiresApproval() {
        HitlSubSystem hitl = new HitlSubSystem();
        hitl.configure(new SubSystemRegistry());
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        policy.configure(Set.of("dangerous"), Set.of());
        ReflectionTestUtils.setField(hitl, "hitlPolicy", policy);
        ReflectionTestUtils.setField(hitl, "hitlGate", new InMemoryHitlGate());

        TaskContext ctx = TaskContext.builder()
                .taskId("t1")
                .vesselId("v1")
                .profile(null)
                .registry(new SubSystemRegistry())
                .build();
        SpiToolCall tc = SpiToolCall.builder().id("c1").name("dangerous").arguments(Map.of("x", 1)).build();
        HitlEvaluation eval = hitl.evaluate(List.of(tc), ctx);
        assertTrue(eval.hasSuspensions());
        assertEquals(1, eval.getTicket().getItems().size());
        assertEquals("dangerous", eval.getTicket().getItems().get(0).getToolName());
    }

    @Test
    void loadGlobalHitlConfigAppliesGlobalDefaults() {
        HitlSubSystem hitl = new HitlSubSystem();
        hitl.configure(new SubSystemRegistry());
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        ReflectionTestUtils.setField(hitl, "hitlPolicy", policy);
        ReflectionTestUtils.setField(hitl, "hitlGate", new InMemoryHitlGate());

        HitlConfig globalHitl = new HitlConfig();
        globalHitl.setRequire(List.of("execute"));
        globalHitl.setSkip(List.of("readFile"));

        GlobalConfig globalConfig = new GlobalConfig();
        globalConfig.setHitl(globalHitl);

        GlobalConfigLoader loader = mock(GlobalConfigLoader.class);
        when(loader.load(any())).thenReturn(globalConfig);
        ReflectionTestUtils.setField(hitl, "globalConfigLoader", loader);
        ReflectionTestUtils.setField(hitl, "globalHitlLoaded", false);

        hitl.loadGlobalHitlConfig();

        TaskContext ctx = TaskContext.builder()
                .taskId("t1")
                .vesselId("v2")
                .profile(null)
                .registry(new SubSystemRegistry())
                .build();
        assertTrue(hitl.evaluate(List.of(SpiToolCall.builder().id("c1").name("execute").arguments(Map.of()).build()), ctx).hasSuspensions());
        assertFalse(hitl.evaluate(List.of(SpiToolCall.builder().id("c2").name("readFile").arguments(Map.of()).build()), ctx).hasSuspensions());
        assertFalse(hitl.evaluate(List.of(SpiToolCall.builder().id("c3").name("calculate").arguments(Map.of()).build()), ctx).hasSuspensions());
    }
}
