package com.meta_claw.knowledge.core.application.flow.worker;

import com.meta_claw.knowledge.core.application.flow.context.SubmitWorkerJobFlowContext;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SubmitWorkerJobNode extends NodeComponent {

    @Override
    public void process() {
        SubmitWorkerJobFlowContext context = this.getContextBean(SubmitWorkerJobFlowContext.class);
        log.debug("Submitting worker job {} for space {}",
                context.getWorkerJob().getJobId(),
                context.getWorkerJob().getSpaceId());
        context.setResult(context.getWorkerJob());
    }
}
