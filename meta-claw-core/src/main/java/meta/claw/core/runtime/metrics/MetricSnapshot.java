package meta.claw.core.runtime.metrics;

import lombok.Builder;
import lombok.Getter;
import meta.claw.core.llm.SpiUsage;

/**
 * 单次任务指标快照。
 * <p>供后续详细指标扩展（如 token 消耗、LLM 延迟、工具调用次数）。</p>
 */
@Builder
@Getter
public class MetricSnapshot {

    private final String vesselId;
    private final String taskId;
    private final int stepCount;
    private final long durationMs;
    private final SpiUsage tokenUsage;
}
