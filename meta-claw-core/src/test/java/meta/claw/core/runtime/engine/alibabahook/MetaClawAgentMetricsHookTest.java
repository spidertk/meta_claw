package meta.claw.core.runtime.engine.alibabahook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.bundle.VesselConfigBundle;
import meta.claw.core.runtime.StepRecord;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.VesselProfile;
import meta.claw.core.runtime.VesselTask;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetaClawAgentMetricsHookTest {

    @Test
    void afterAgentRecordsTaskMetrics() {
        MetricsRecorder recorder = new MetricsRecorder();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        org.springframework.test.util.ReflectionTestUtils.setField(recorder, "meterRegistry", registry);

        TaskContext ctx = dummyContext();
        ctx.getSteps().add(StepRecord.builder().stepNumber(1).action("llm").description("call").build());
        ctx.getSteps().add(StepRecord.builder().stepNumber(2).action("tool").description("exec").build());

        MetaClawAgentMetricsHook hook = new MetaClawAgentMetricsHook(ctx, recorder);
        hook.afterAgent(new OverAllState(), RunnableConfig.builder().build());

        assertEquals(1.0, registry.counter("agent.task.completed", "vessel", "v1").count());
        assertEquals(2.0, registry.counter("agent.steps", "vessel", "v1").count());
        assertEquals(1, registry.timer("agent.task.duration", "vessel", "v1").count());
    }

    @Test
    void getNameReturnsExpectedValue() {
        MetaClawAgentMetricsHook hook = new MetaClawAgentMetricsHook(dummyContext(), null);
        assertEquals("meta-claw-agent-metrics-hook", hook.getName());
    }

    private TaskContext dummyContext() {
        VesselTask task = VesselTask.builder()
                .taskId("t1")
                .vesselId("v1")
                .sessionId("s1")
                .userMessage("hi")
                .build();
        VesselProfile profile = mock(VesselProfile.class);
        meta.claw.core.config.RuntimeConfig runtimeConfig = new meta.claw.core.config.RuntimeConfig();
        runtimeConfig.setProviderConfig(new ProviderConfig());
        VesselConfigBundle bundle = VesselConfigBundle.builder()
                .runtimeConfig(runtimeConfig)
                .build();
        when(profile.getBundle()).thenReturn(bundle);
        return new TaskContext(task, profile, null);
    }
}
