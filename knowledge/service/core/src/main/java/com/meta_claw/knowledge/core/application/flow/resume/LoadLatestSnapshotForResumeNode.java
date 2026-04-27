package com.meta_claw.knowledge.core.application.flow.resume;

import com.meta_claw.knowledge.core.application.flow.context.ResumeSnapshotScanFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.FlowRuntimeDependencies;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.yomahub.liteflow.core.NodeComponent;
public class LoadLatestSnapshotForResumeNode extends NodeComponent {

    @Override
    public void process() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        FlowRuntimeDependencies runtimeDependencies = context.getRuntimeDependencies();
        String latestSnapshotId = context.getSourceRecord().getLatestSnapshotId();
        if (latestSnapshotId == null || latestSnapshotId.isBlank()) {
            context.setResumeNeeded(true);
            return;
        }

        SnapshotRecord latestSnapshot = runtimeDependencies.getSnapshotStoreRepository().findById(latestSnapshotId)
                .orElseThrow(() -> new IllegalStateException("Missing latest snapshot: " + latestSnapshotId));
        context.setLatestSnapshot(latestSnapshot);
    }
}
