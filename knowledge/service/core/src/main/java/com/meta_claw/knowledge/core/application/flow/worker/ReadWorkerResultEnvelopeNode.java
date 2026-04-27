package com.meta_claw.knowledge.core.application.flow.worker;

import com.meta_claw.knowledge.core.application.flow.context.IngestWorkerResultFlowContext;
import com.yomahub.liteflow.core.NodeComponent;

public class ReadWorkerResultEnvelopeNode extends NodeComponent {

    @Override
    public void process() {
        IngestWorkerResultFlowContext context = this.getContextBean(IngestWorkerResultFlowContext.class);
        context.setWorkerResult(context.getEnvelope().toDomain());
    }
}
