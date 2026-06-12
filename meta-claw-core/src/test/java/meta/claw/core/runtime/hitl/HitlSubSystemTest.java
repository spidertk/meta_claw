package meta.claw.core.runtime.hitl;

import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselTask;
import meta.claw.core.runtime.subsystem.HitlSubSystem;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.tool.SpiToolCall;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class HitlSubSystemTest {

    @Test
    void evaluateApprovesWhenPolicyAutoApproves() {
        HitlSubSystem hitl = new HitlSubSystem();
        hitl.configure(new SubSystemRegistry());
        ConfigurableHitlPolicy policy = new ConfigurableHitlPolicy();
        ReflectionTestUtils.setField(hitl, "hitlPolicy", policy);
        ReflectionTestUtils.setField(hitl, "hitlGate", new InMemoryHitlGate());

        TaskContext ctx = new TaskContext(
                VesselTask.builder().taskId("t1").vesselId("v1").build(),
                null,
                new SubSystemRegistry()
        );
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

        TaskContext ctx = new TaskContext(
                VesselTask.builder().taskId("t1").vesselId("v1").build(),
                null,
                new SubSystemRegistry()
        );
        SpiToolCall tc = SpiToolCall.builder().id("c1").name("dangerous").arguments(Map.of("x", 1)).build();
        HitlEvaluation eval = hitl.evaluate(List.of(tc), ctx);
        assertTrue(eval.hasSuspensions());
        assertEquals(1, eval.getTicket().getItems().size());
        assertEquals("dangerous", eval.getTicket().getItems().get(0).getToolName());
    }
}
