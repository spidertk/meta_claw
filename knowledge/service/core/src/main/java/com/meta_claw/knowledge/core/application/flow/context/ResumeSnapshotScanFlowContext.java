package com.meta_claw.knowledge.core.application.flow.context;

import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SourceRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class ResumeSnapshotScanFlowContext {

    private String sourceId;
    private SourceRecord sourceRecord;
    private SnapshotRecord latestSnapshot;
    private SnapshotRecord nextSnapshot;
    private SnapshotRecord resultSnapshot;
    private Boolean resumeNeeded;
    private FlowRuntimeDependencies runtimeDependencies;
}
