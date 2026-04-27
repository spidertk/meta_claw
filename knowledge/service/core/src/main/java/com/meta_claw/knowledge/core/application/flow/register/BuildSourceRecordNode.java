package com.meta_claw.knowledge.core.application.flow.register;

import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.intake.SourceSnapshotScanner;
import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.yomahub.liteflow.core.NodeComponent;

public class BuildSourceRecordNode extends NodeComponent {

    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        SourceRecord incomingSourceRecord = context.getRequest().toDomain(context.getBinding().getSpaceId());
        String sourceId = SourceSnapshotScanner.ensureSourceId(incomingSourceRecord);
        SourceRecord resolvedSourceRecord = incomingSourceRecord.toBuilder()
                .sourceId(sourceId)
                .build();
        context.setIncomingSourceRecord(incomingSourceRecord);
        context.setResolvedSourceRecord(resolvedSourceRecord);
    }
}
