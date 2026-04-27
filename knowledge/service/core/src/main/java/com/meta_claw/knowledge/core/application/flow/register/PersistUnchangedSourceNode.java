package com.meta_claw.knowledge.core.application.flow.register;

import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.FlowRuntimeDependencies;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.yomahub.liteflow.core.NodeComponent;
public class PersistUnchangedSourceNode extends NodeComponent {

    @Override
    public boolean isAccess() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        return Boolean.TRUE.equals(context.getUnchanged());
    }

    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        FlowRuntimeDependencies runtimeDependencies = context.getRuntimeDependencies();
        SnapshotRecord latestSnapshot = context.getLatestSnapshot();
        SourceRecord resolvedSourceRecord = context.getResolvedSourceRecord().toBuilder()
                .status("unchanged")
                .latestSnapshotId(latestSnapshot.getSnapshotId())
                .build();
        runtimeDependencies.getSourceRegistryRepository().save(resolvedSourceRecord);
        context.setResolvedSourceRecord(resolvedSourceRecord);
    }
}
