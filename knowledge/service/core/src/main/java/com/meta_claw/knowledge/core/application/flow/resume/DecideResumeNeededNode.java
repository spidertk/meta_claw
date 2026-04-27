package com.meta_claw.knowledge.core.application.flow.resume;

import com.meta_claw.knowledge.core.application.flow.context.ResumeSnapshotScanFlowContext;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.yomahub.liteflow.core.NodeComponent;

public class DecideResumeNeededNode extends NodeComponent {

    @Override
    public void process() {
        ResumeSnapshotScanFlowContext context = this.getContextBean(ResumeSnapshotScanFlowContext.class);
        SnapshotRecord latestSnapshot = context.getLatestSnapshot();
        boolean resumeNeeded = latestSnapshot == null
                || ("partial".equals(latestSnapshot.getScanStatus())
                && latestSnapshot.getNextScanCursor() != null
                && !latestSnapshot.getNextScanCursor().isBlank());
        context.setResumeNeeded(resumeNeeded);
    }
}
