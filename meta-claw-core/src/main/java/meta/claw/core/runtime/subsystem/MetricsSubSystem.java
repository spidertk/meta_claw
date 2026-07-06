package meta.claw.core.runtime.subsystem;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.prompt.PromptVars;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.metrics.MetricSnapshot;
import meta.claw.core.runtime.metrics.MetricsRecorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Metrics 子系统。
 * <p>在任务结束时通过 Micrometer 记录任务完成数与执行步数，并输出任务级指标快照。</p>
 */
@Slf4j
@Component
public class MetricsSubSystem implements VesselSubSystem {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    private MetricsRecorder metricsRecorder;

    @Override
    public String name() {
        return "metrics";
    }

    @Override
    public void configure(SubSystemRegistry registry) {
        // no-op
    }

    @Override
    public PromptVars promptVars() {
        return PromptVars.empty();
    }

    @Override
    public int priority() {
        return 40;
    }

    @Override
    public void onTaskEnd(TaskContext ctx) {
        String vesselId = ctx.getVesselId();
        int steps = ctx.getSteps().size();

        if (metricsRecorder != null) {
            metricsRecorder.recordTaskCompleted(vesselId);
            metricsRecorder.recordSteps(vesselId, steps);
            metricsRecorder.recordTaskDuration(vesselId, ctx.getDurationMs());
        } else if (meterRegistry != null) {
            meterRegistry.counter("agent.task.completed", "vessel", vesselId).increment();
            meterRegistry.counter("agent.steps", "vessel", vesselId).increment(steps);
        }

        MetricSnapshot snapshot = MetricSnapshot.builder()
                .vesselId(vesselId)
                .taskId(ctx.getTaskId())
                .stepCount(steps)
                .toolCallCount(ctx.getToolCallCount())
                .durationMs(ctx.getDurationMs())
                .tokenUsage(ctx.getTotalTokenUsage())
                .build();

        log.debug("Recorded metrics snapshot for vessel {}: {}", vesselId, snapshot);
    }
}
