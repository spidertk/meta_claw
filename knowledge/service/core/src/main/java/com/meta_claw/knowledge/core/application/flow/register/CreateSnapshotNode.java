package com.meta_claw.knowledge.core.application.flow.register;

import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.FlowRuntimeDependencies;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeRequest;
import com.meta_claw.knowledge.core.application.intake.SourceSnapshotScanner;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.yomahub.liteflow.core.NodeComponent;

public class CreateSnapshotNode extends NodeComponent {

    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        FlowRuntimeDependencies runtimeDependencies = context.getRuntimeDependencies();
        SnapshotRecord candidateSnapshot = SourceSnapshotScanner.createSnapshot(SourceIntakeRequest.builder()
                .sourceRecord(context.getResolvedSourceRecord())
                .scanCursor(null)
                .config(runtimeDependencies.getSourceIntakeConfig())
                .build());
        context.setCandidateSnapshot(candidateSnapshot);
    }
}
