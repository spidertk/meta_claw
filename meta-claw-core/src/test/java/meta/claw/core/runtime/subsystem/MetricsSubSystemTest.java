package meta.claw.core.runtime.subsystem;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselTask;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class MetricsSubSystemTest {

    @Test
    void recordsTaskCompletionAndSteps() {
        MetricsSubSystem metricsSub = new MetricsSubSystem();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(metricsSub, "meterRegistry", registry);

        TaskContext ctx = new TaskContext(
                VesselTask.builder().taskId("t1").vesselId("v1").sessionId("s1").userMessage("hi").build(),
                null,
                new SubSystemRegistry()
        );
        ctx.getSteps().add(meta.claw.core.runtime.StepRecord.builder().stepNumber(1).build());
        ctx.getSteps().add(meta.claw.core.runtime.StepRecord.builder().stepNumber(2).build());

        metricsSub.onTaskEnd(ctx);

        assertEquals(1.0, registry.counter("agent.task.completed", "vessel", "v1").count());
        assertEquals(2.0, registry.counter("agent.steps", "vessel", "v1").count());
    }

    @Test
    void nameAndPriorityAreCorrect() {
        MetricsSubSystem metricsSub = new MetricsSubSystem();
        assertEquals("metrics", metricsSub.name());
        assertEquals(40, metricsSub.priority());
        assertTrue(metricsSub.promptVars().toMap().isEmpty());
    }
}
