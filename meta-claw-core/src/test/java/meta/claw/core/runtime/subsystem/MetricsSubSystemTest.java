package meta.claw.core.runtime.subsystem;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MetricsSubSystemTest {

    @Test
    void recordsTaskCompletionAndSteps() {
        MetricsSubSystem metricsSub = new MetricsSubSystem();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(metricsSub, "meterRegistry", registry);

        TaskContext ctx = TaskContext.builder()
                .taskId("t1")
                .vesselId("v1")
                .sessionId("s1")
                .userMessage("hi")
                .profile(null)
                .registry(new SubSystemRegistry())
                .build();
        ctx.getSteps().add(meta.claw.core.runtime.StepRecord.builder().stepNumber(1).build());
        ctx.getSteps().add(meta.claw.core.runtime.StepRecord.builder().stepNumber(2).build());

        metricsSub.onTaskEnd(ctx);

        assertEquals(1.0, registry.counter("agent.task.completed", "vessel", "v1").count());
        assertEquals(2.0, registry.counter("agent.steps", "vessel", "v1").count());
    }

    @Test
    void delegatesToMetricsRecorderAndBuildsSnapshot() {
        MetricsSubSystem metricsSub = new MetricsSubSystem();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MetricsRecorder recorder = new MetricsRecorder();
        ReflectionTestUtils.setField(recorder, "meterRegistry", registry);
        ReflectionTestUtils.setField(metricsSub, "metricsRecorder", recorder);

        TaskContext ctx = TaskContext.builder()
                .taskId("t1")
                .vesselId("v1")
                .sessionId("s1")
                .userMessage("hi")
                .profile(null)
                .registry(new SubSystemRegistry())
                .build();
        ctx.getSteps().add(meta.claw.core.runtime.StepRecord.builder().stepNumber(1).build());
        ctx.accumulateToolCalls(List.of(meta.claw.core.tool.SpiToolCall.builder().id("c1").name("calc").build()));
        ctx.accumulateTokenUsage(SpiUsage.builder().promptTokens(10).completionTokens(5).totalTokens(15).build());

        metricsSub.onTaskEnd(ctx);

        assertEquals(1.0, registry.counter("agent.task.completed", "vessel", "v1").count());
        assertEquals(1.0, registry.counter("agent.steps", "vessel", "v1").count());
        assertTrue(registry.timer("agent.task.duration", "vessel", "v1").count() >= 0);
        assertEquals(1, ctx.getToolCallCount());
        assertEquals(15, ctx.getTotalTokenUsage().totalTokens());
    }

    @Test
    void nameAndPriorityAreCorrect() {
        MetricsSubSystem metricsSub = new MetricsSubSystem();
        assertEquals("metrics", metricsSub.name());
        assertEquals(40, metricsSub.priority());
        assertTrue(metricsSub.promptVars().toMap().isEmpty());
    }
}
