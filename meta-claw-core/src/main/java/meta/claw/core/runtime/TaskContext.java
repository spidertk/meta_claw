package meta.claw.core.runtime;

import lombok.Getter;
import meta.claw.core.llm.SpiUsage;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.VesselSubSystem;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 任务执行上下文。
 * <p>单次 chat()/execute() 调用的执行工作区。</p>
 */
@Getter
public class TaskContext {

    private final VesselTask task;
    private final VesselProfile profile;
    private final SubSystemRegistry registry;
    private final MessageThread messages;
    private final StepLog steps;
    private final AtomicInteger toolCallCount = new AtomicInteger(0);
    private final AtomicReference<SpiUsage> totalTokenUsage = new AtomicReference<>(
            SpiUsage.builder().promptTokens(0).completionTokens(0).totalTokens(0).build());
    private final long startTime = System.currentTimeMillis();

    public TaskContext(VesselTask task, VesselProfile profile, SubSystemRegistry registry) {
        this.task = task;
        this.profile = profile;
        this.registry = registry;
        this.messages = new MessageThread();
        this.steps = new StepLog();
    }

    @SuppressWarnings("unchecked")
    public <T extends VesselSubSystem> T getSubSystem(String name) {
        return registry.get(name);
    }

    public void incrementToolCallCount() {
        toolCallCount.incrementAndGet();
    }

    public int getToolCallCount() {
        return toolCallCount.get();
    }

    public void addTokenUsage(SpiUsage usage) {
        if (usage == null) {
            return;
        }
        totalTokenUsage.updateAndGet(current -> SpiUsage.builder()
                .promptTokens(nullToZero(current.promptTokens()) + nullToZero(usage.promptTokens()))
                .completionTokens(nullToZero(current.completionTokens()) + nullToZero(usage.completionTokens()))
                .totalTokens(nullToZero(current.totalTokens()) + nullToZero(usage.totalTokens()))
                .build());
    }

    public SpiUsage getTotalTokenUsage() {
        return totalTokenUsage.get();
    }

    public long getDurationMs() {
        return System.currentTimeMillis() - startTime;
    }

    private static int nullToZero(Integer value) {
        return value != null ? value : 0;
    }
}
