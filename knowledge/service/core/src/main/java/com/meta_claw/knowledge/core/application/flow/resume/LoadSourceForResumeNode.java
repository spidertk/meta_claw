package com.meta_claw.knowledge.core.application.flow.resume;

import com.meta_claw.knowledge.core.application.flow.context.ResumeSnapshotScanFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.FlowRuntimeDependencies;
import com.yomahub.liteflow.core.NodeComponent;
public class LoadSourceForResumeNode extends NodeComponent {

    @Override
    public void process() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        FlowRuntimeDependencies runtimeDependencies = context.getRuntimeDependencies();
        context.setSourceRecord(runtimeDependencies.getSourceRegistryRepository().findById(context.getSourceId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown source: " + context.getSourceId())));
    }
}
