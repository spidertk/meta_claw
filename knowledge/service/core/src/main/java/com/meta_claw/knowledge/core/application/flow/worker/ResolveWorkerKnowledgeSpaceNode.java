package com.meta_claw.knowledge.core.application.flow.worker;

import com.meta_claw.knowledge.core.application.flow.context.SubmitWorkerJobFlowContext;
import com.meta_claw.knowledge.core.application.flow.support.RoleBindingFlowSupport;
import com.yomahub.liteflow.core.NodeComponent;

public class ResolveWorkerKnowledgeSpaceNode extends NodeComponent {

    private final RoleBindingFlowSupport roleBindingFlowSupport = new RoleBindingFlowSupport();

    @Override
    public void process() {
        SubmitWorkerJobFlowContext context = this.getContextBean(SubmitWorkerJobFlowContext.class);
        roleBindingFlowSupport.resolveAndBind(context);
    }
}
