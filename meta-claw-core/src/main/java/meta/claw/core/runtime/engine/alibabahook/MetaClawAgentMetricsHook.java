package meta.claw.core.runtime.engine.alibabahook;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import meta.claw.core.runtime.TaskContext;
import meta.claw.core.runtime.metrics.MetricsRecorder;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 在 SAA Agent 执行前后记录任务级指标。
 *
 * <p>记录任务完成数、步数、任务时长，所有指标均带 vessel 标签。</p>
 */
public class MetaClawAgentMetricsHook extends AgentHook {

    private final TaskContext ctx;
    private final MetricsRecorder metricsRecorder;

    public MetaClawAgentMetricsHook(TaskContext ctx, MetricsRecorder metricsRecorder) {
        this.ctx = ctx;
        this.metricsRecorder = metricsRecorder;
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
        if (metricsRecorder != null) {
            String vesselId = ctx.getTask().getVesselId();
            metricsRecorder.recordTaskCompleted(vesselId);
            metricsRecorder.recordSteps(vesselId, ctx.getSteps().size());
            metricsRecorder.recordTaskDuration(vesselId, ctx.getDurationMs());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String getName() {
        return "meta-claw-agent-metrics-hook";
    }
}
