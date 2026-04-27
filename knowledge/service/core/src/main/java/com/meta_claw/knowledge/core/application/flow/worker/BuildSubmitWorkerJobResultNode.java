package com.meta_claw.knowledge.core.application.flow.worker;

import com.meta_claw.knowledge.core.application.flow.context.SubmitWorkerJobFlowContext;
import com.yomahub.liteflow.core.NodeComponent;

public class BuildSubmitWorkerJobResultNode extends NodeComponent {

    @Override
    public void process() {
        SubmitWorkerJobFlowContext context = this.getContextBean(SubmitWorkerJobFlowContext.class);
        if (context.getResult() == null) {
            context.setResult(context.getWorkerJob());
        }
    }
}
