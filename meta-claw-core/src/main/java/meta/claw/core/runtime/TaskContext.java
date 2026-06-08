package meta.claw.core.runtime;

import lombok.Getter;
import meta.claw.core.runtime.subsystem.SubSystemRegistry;
import meta.claw.core.runtime.subsystem.VesselSubSystem;

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
}
