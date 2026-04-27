package com.meta_claw.knowledge.core.application.flow.register;

import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.FlowRuntimeDependencies;
import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.yomahub.liteflow.core.NodeComponent;
public class PersistChangedSourceAndSnapshotNode extends NodeComponent {

    @Override
    public boolean isAccess() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        return !Boolean.TRUE.equals(context.getUnchanged());
    }

    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        FlowRuntimeDependencies runtimeDependencies = context.getRuntimeDependencies();
        SourceRecord resolvedSourceRecord = context.getResolvedSourceRecord().toBuilder()
                .latestSnapshotId(context.getCandidateSnapshot().getSnapshotId())
                .build();
        runtimeDependencies.getSourceRegistryRepository().save(resolvedSourceRecord);
        runtimeDependencies.getSnapshotStoreRepository().save(context.getCandidateSnapshot());
        context.setResolvedSourceRecord(resolvedSourceRecord);
    }
}
