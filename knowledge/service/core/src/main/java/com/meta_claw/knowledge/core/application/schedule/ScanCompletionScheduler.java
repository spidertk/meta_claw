package com.meta_claw.knowledge.core.application.schedule;

import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ScanCompletionScheduler {

    private final KnowledgeFlowFacade flowFacade;

    public ScanCompletionScheduler(KnowledgeFlowFacade flowFacade) {
        this.flowFacade = flowFacade;
    }

    /**
     * 对指定 source 执行 resume，直到 scanStatus 变为 complete。
     * @return 实际执行的 resume 次数（含首次）
     */
    public int completeScan(String sourceId) {
        int count = 0;
        SnapshotRecord snapshot;

        do {
            snapshot = flowFacade.resumeSnapshotScan(sourceId);
            count++;
            log.info("Resume scan #{} for source={}, status={}, cursor={}",
                    count, sourceId, snapshot.getScanStatus(), snapshot.getNextScanCursor());
        } while ("partial".equals(snapshot.getScanStatus()));

        return count;
    }
}
