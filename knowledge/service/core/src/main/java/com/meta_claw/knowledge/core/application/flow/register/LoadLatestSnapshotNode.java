package com.meta_claw.knowledge.core.application.flow.register;

import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.FlowRuntimeDependencies;
import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.yomahub.liteflow.core.NodeComponent;
public class LoadLatestSnapshotNode extends NodeComponent {

    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        FlowRuntimeDependencies runtimeDependencies = context.getRuntimeDependencies();
        String sourceId = context.getResolvedSourceRecord().getSourceId();
        runtimeDependencies.getSourceRegistryRepository().findById(sourceId).ifPresent(existingSourceRecord -> {
            SourceRecord resolvedSourceRecord = context.getResolvedSourceRecord().toBuilder()
                    .status("partial_update")
                    .createdAt(existingSourceRecord.getCreatedAt())
                    .latestSnapshotId(existingSourceRecord.getLatestSnapshotId())
                    .build();
            context.setResolvedSourceRecord(resolvedSourceRecord);
            runtimeDependencies.getSnapshotStoreRepository().findLatestBySourceId(sourceId).ifPresent(context::setLatestSnapshot);
        });
    }
}
