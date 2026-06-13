package meta.claw.core.runtime.metrics;

import lombok.Builder;
import lombok.Getter;
import meta.claw.core.llm.SpiUsage;

/**
 * 单次任务指标快照。
 * <p>封装任务级汇总指标，供日志、调试与后续分析使用。</p>
 */
@Builder
@Getter
public class MetricSnapshot {

    private final String vesselId;
    private final String taskId;
    private final int stepCount;
    private final int toolCallCount;
    private final long durationMs;
    private final SpiUsage tokenUsage;
}
