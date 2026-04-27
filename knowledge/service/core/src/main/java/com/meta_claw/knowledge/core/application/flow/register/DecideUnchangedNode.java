package com.meta_claw.knowledge.core.application.flow.register;

import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.yomahub.liteflow.core.NodeComponent;

public class DecideUnchangedNode extends NodeComponent {

    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        boolean unchanged = context.getLatestSnapshot() != null
                && context.getLatestSnapshot().getContentFingerprint().equals(context.getCandidateSnapshot().getContentFingerprint());
        context.setUnchanged(unchanged);
    }
}
