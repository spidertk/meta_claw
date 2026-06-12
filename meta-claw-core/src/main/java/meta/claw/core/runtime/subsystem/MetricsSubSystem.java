package meta.claw.core.runtime.subsystem;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.prompt.PromptVars;
import meta.claw.core.runtime.TaskContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Metrics 子系统。
 * <p>在任务结束时通过 Micrometer 记录任务完成数与执行步数。</p>
 */
@Slf4j
@Component
public class MetricsSubSystem implements VesselSubSystem {

    @Autowired
    private MeterRegistry meterRegistry;

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
        if (meterRegistry == null) {
            return;
        }
        String vesselId = ctx.getTask().getVesselId();
        meterRegistry.counter("agent.task.completed", "vessel", vesselId).increment();
        meterRegistry.counter("agent.steps", "vessel", vesselId).increment(ctx.getSteps().size());
        log.debug("Recorded metrics for vessel {}: steps={}", vesselId, ctx.getSteps().size());
    }
}
