package com.meta_claw.knowledge.core.application.flow.resume;

import com.meta_claw.knowledge.core.application.flow.context.ResumeSnapshotScanFlowContext;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.yomahub.liteflow.core.NodeComponent;

public class BuildResumeResultNode extends NodeComponent {

    @Override
    public void process() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        SnapshotRecord resultSnapshot = Boolean.TRUE.equals(context.getResumeNeeded()) && context.getNextSnapshot() != null
                ? context.getNextSnapshot()
                : context.getLatestSnapshot();
        context.setResultSnapshot(resultSnapshot);
    }
}
