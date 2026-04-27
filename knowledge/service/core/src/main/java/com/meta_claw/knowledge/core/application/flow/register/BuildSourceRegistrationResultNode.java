package com.meta_claw.knowledge.core.application.flow.register;

import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import com.yomahub.liteflow.core.NodeComponent;

public class BuildSourceRegistrationResultNode extends NodeComponent {

    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        SnapshotRecord snapshotRecord = Boolean.TRUE.equals(context.getUnchanged())
                ? context.getLatestSnapshot()
                : context.getCandidateSnapshot();
        context.setResult(SourceRegistrationResult.builder()
                .sourceRecord(context.getResolvedSourceRecord())
                .snapshotRecord(snapshotRecord)
                .unchanged(Boolean.TRUE.equals(context.getUnchanged()))
                .build());
    }
}
