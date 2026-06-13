package meta.claw.core.runtime.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.llm.SpiUsage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 运行时指标记录器。
 * <p>统一封装 Micrometer 指标写入，供 LLM 调用、工具执行、任务生命周期各组件使用。</p>
 */
@Slf4j
@Component
public class MetricsRecorder {

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    public void recordTaskCompleted(String vesselId) {
        if (meterRegistry == null) {
            return;
        }
        counter("agent.task.completed", vesselId).increment();
    }

    public void recordSteps(String vesselId, int steps) {
        if (meterRegistry == null) {
            return;
        }
        counter("agent.steps", vesselId).increment(steps);
    }

    public void recordTaskDuration(String vesselId, long durationMs) {
        if (meterRegistry == null) {
            return;
        }
        timer("agent.task.duration", vesselId).record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordLlmLatency(String vesselId, long durationMs) {
        if (meterRegistry == null) {
            return;
        }
        timer("agent.llm.latency", vesselId).record(durationMs, TimeUnit.MILLISECONDS);
        log.debug("Recorded LLM latency for vessel {}: {}ms", vesselId, durationMs);
    }

    public void recordTokenUsage(String vesselId, SpiUsage usage) {
        if (meterRegistry == null || usage == null) {
            return;
        }
        counter("agent.llm.tokens.prompt", vesselId).increment(nullToZero(usage.promptTokens()));
        counter("agent.llm.tokens.completion", vesselId).increment(nullToZero(usage.completionTokens()));
        counter("agent.llm.tokens.total", vesselId).increment(nullToZero(usage.totalTokens()));
        log.debug("Recorded token usage for vessel {}: {}", vesselId, usage);
    }

    public void recordToolCall(String vesselId, String toolName) {
        if (meterRegistry == null || toolName == null) {
            return;
        }
        Counter counter = Counter.builder("agent.tool.calls")
                .tag("vessel", vesselId)
                .tag("tool", toolName)
                .register(meterRegistry);
        counter.increment();
        log.debug("Recorded tool call for vessel {}: {}", vesselId, toolName);
    }

    private Counter counter(String name, String vesselId) {
        return Counter.builder(name)
                .tag("vessel", vesselId)
                .register(meterRegistry);
    }

    private Timer timer(String name, String vesselId) {
        return Timer.builder(name)
                .tag("vessel", vesselId)
                .register(meterRegistry);
    }

    private static double nullToZero(Integer value) {
        return value != null ? value.doubleValue() : 0.0;
    }
}
