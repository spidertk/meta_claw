package meta.claw.core.runtime.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import meta.claw.core.llm.SpiUsage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class MetricsRecorderTest {

    @Test
    void recordsTaskCompletionAndSteps() {
        MetricsRecorder recorder = new MetricsRecorder();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(recorder, "meterRegistry", registry);

        recorder.recordTaskCompleted("v1");
        recorder.recordSteps("v1", 3);

        assertEquals(1.0, registry.counter("agent.task.completed", "vessel", "v1").count());
        assertEquals(3.0, registry.counter("agent.steps", "vessel", "v1").count());
    }

    @Test
    void recordsLlmLatency() {
        MetricsRecorder recorder = new MetricsRecorder();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(recorder, "meterRegistry", registry);

        recorder.recordLlmLatency("v1", 150);

        assertEquals(1, registry.timer("agent.llm.latency", "vessel", "v1").count());
        assertEquals(150.0, registry.timer("agent.llm.latency", "vessel", "v1").totalTime(TimeUnit.MILLISECONDS));
    }

    @Test
    void recordsTokenUsage() {
        MetricsRecorder recorder = new MetricsRecorder();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(recorder, "meterRegistry", registry);

        recorder.recordTokenUsage("v1", SpiUsage.builder().promptTokens(10).completionTokens(5).totalTokens(15).build());

        assertEquals(10.0, registry.counter("agent.llm.tokens.prompt", "vessel", "v1").count());
        assertEquals(5.0, registry.counter("agent.llm.tokens.completion", "vessel", "v1").count());
        assertEquals(15.0, registry.counter("agent.llm.tokens.total", "vessel", "v1").count());
    }

    @Test
    void handlesNullUsageGracefully() {
        MetricsRecorder recorder = new MetricsRecorder();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(recorder, "meterRegistry", registry);

        recorder.recordTokenUsage("v1", null);

        assertNull(registry.find("agent.llm.tokens.total").tags("vessel", "v1").counter());
    }

    @Test
    void recordsToolCallsWithToolTag() {
        MetricsRecorder recorder = new MetricsRecorder();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReflectionTestUtils.setField(recorder, "meterRegistry", registry);

        recorder.recordToolCall("v1", "calc");
        recorder.recordToolCall("v1", "calc");
        recorder.recordToolCall("v1", "search");

        assertEquals(2.0, registry.counter("agent.tool.calls", "vessel", "v1", "tool", "calc").count());
        assertEquals(1.0, registry.counter("agent.tool.calls", "vessel", "v1", "tool", "search").count());
    }

    @Test
    void noOpWhenMeterRegistryMissing() {
        MetricsRecorder recorder = new MetricsRecorder();
        // meterRegistry is null
        assertDoesNotThrow(() -> {
            recorder.recordTaskCompleted("v1");
            recorder.recordSteps("v1", 1);
            recorder.recordLlmLatency("v1", 100);
            recorder.recordTokenUsage("v1", SpiUsage.builder().totalTokens(1).build());
            recorder.recordToolCall("v1", "calc");
        });
    }
}
