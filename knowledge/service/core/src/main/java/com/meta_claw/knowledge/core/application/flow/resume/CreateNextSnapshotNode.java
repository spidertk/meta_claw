package com.meta_claw.knowledge.core.application.flow.resume;

import com.meta_claw.knowledge.core.application.flow.context.ResumeSnapshotScanFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.FlowRuntimeDependencies;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeRequest;
import com.meta_claw.knowledge.core.application.intake.SourceSnapshotScanner;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.yomahub.liteflow.core.NodeComponent;

public class CreateNextSnapshotNode extends NodeComponent {

    @Override
    public boolean isAccess() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        return Boolean.TRUE.equals(context.getResumeNeeded());
    }

    @Override
    public void process() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        FlowRuntimeDependencies runtimeDependencies = context.getRuntimeDependencies();
        String cursor = context.getLatestSnapshot() == null ? null : context.getLatestSnapshot().getNextScanCursor();
        SnapshotRecord nextSnapshot = SourceSnapshotScanner.createSnapshot(SourceIntakeRequest.builder()
                .sourceRecord(context.getSourceRecord())
                .scanCursor(cursor)
                .config(runtimeDependencies.getSourceIntakeConfig())
                .build());
        context.setNextSnapshot(nextSnapshot);
    }
}
