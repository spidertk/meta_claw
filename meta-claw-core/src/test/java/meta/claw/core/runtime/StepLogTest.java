package meta.claw.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StepLogTest {

    @Test
    void addAndSnapshot() {
        StepLog log = new StepLog();
        log.add(StepRecord.builder().stepNumber(1).action("think").build());
        assertEquals(1, log.size());
        assertEquals("think", log.snapshot().get(0).getAction());
    }

    @Test
    void snapshot_isImmutable() {
        StepLog log = new StepLog();
        log.add(StepRecord.builder().stepNumber(1).action("think").build());
        assertThrows(UnsupportedOperationException.class,
                () -> log.snapshot().add(StepRecord.builder().stepNumber(2).build()));
    }
}
