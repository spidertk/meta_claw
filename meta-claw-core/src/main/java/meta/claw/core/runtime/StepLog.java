package meta.claw.core.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * 步骤日志封装（替代裸 List<StepRecord>）。
 */
public class StepLog {

    private final List<StepRecord> steps = new ArrayList<>();

    public void add(StepRecord step) {
        steps.add(step);
    }

    public List<StepRecord> snapshot() {
        return List.copyOf(steps);
    }

    public int size() {
        return steps.size();
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }
}
