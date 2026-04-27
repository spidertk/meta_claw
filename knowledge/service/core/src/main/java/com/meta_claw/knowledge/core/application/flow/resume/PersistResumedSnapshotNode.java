package com.meta_claw.knowledge.core.application.flow.resume;

import com.meta_claw.knowledge.core.application.flow.context.ResumeSnapshotScanFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.FlowRuntimeDependencies;
import com.yomahub.liteflow.core.NodeComponent;
public class PersistResumedSnapshotNode extends NodeComponent {

    @Override
    public boolean isAccess() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        return Boolean.TRUE.equals(context.getResumeNeeded()) && context.getNextSnapshot() != null;
    }

    @Override
    public void process() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        FlowRuntimeDependencies runtimeDependencies = context.getRuntimeDependencies();
        runtimeDependencies.getSnapshotStoreRepository().save(context.getNextSnapshot());
        context.getSourceRecord().setLatestSnapshotId(context.getNextSnapshot().getSnapshotId());
        runtimeDependencies.getSourceRegistryRepository().save(context.getSourceRecord());
    }
}
