package com.meta_claw.knowledge.core.application.schedule;

import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ScanCompletionSchedulerTest {

    @Test
    @DisplayName("partial snapshot 触发一次 resume 后变为 complete")
    void resumeUntilComplete() {
        KnowledgeFlowFacade facade = Mockito.mock(KnowledgeFlowFacade.class);

        SnapshotRecord partial = SnapshotRecord.builder()
                .snapshotId("snap_1")
                .sourceId("src_1")
                .scanStatus("partial")
                .nextScanCursor("cursor_1")
                .build();

        SnapshotRecord complete = SnapshotRecord.builder()
                .snapshotId("snap_2")
                .sourceId("src_1")
                .scanStatus("complete")
                .nextScanCursor(null)
                .build();

        when(facade.resumeSnapshotScan("src_1"))
                .thenReturn(partial)
                .thenReturn(complete);

        ScanCompletionScheduler scheduler = new ScanCompletionScheduler(facade);
        int resumedCount = scheduler.completeScan("src_1");

        assertThat(resumedCount).isEqualTo(2);
        verify(facade, times(2)).resumeSnapshotScan("src_1");
    }

    @Test
    @DisplayName("complete snapshot 不触发多余 resume")
    void alreadyComplete() {
        KnowledgeFlowFacade facade = Mockito.mock(KnowledgeFlowFacade.class);
        SnapshotRecord complete = SnapshotRecord.builder()
                .snapshotId("snap_1")
                .sourceId("src_1")
                .scanStatus("complete")
                .build();

        when(facade.resumeSnapshotScan("src_1")).thenReturn(complete);

        ScanCompletionScheduler scheduler = new ScanCompletionScheduler(facade);
        int resumedCount = scheduler.completeScan("src_1");

        assertThat(resumedCount).isEqualTo(1);
        verify(facade, times(1)).resumeSnapshotScan("src_1");
    }
}
