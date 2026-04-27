package com.meta_claw.knowledge.core.application.flow.worker;

import com.meta_claw.knowledge.core.application.flow.context.SubmitWorkerJobFlowContext;
import com.yomahub.liteflow.core.NodeComponent;

public class BuildWorkerJobNode extends NodeComponent {

    @Override
    public void process() {
        SubmitWorkerJobFlowContext context = this.getContextBean(SubmitWorkerJobFlowContext.class);
        context.setWorkerJob(context.getRequest().toDomain(context.getBinding().getSpaceId()));
    }
}
