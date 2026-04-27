package com.meta_claw.knowledge.core.application.flow.register;

import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.support.RoleBindingFlowSupport;
import com.yomahub.liteflow.core.NodeComponent;

public class ResolveKnowledgeSpaceNode extends NodeComponent {

    private final RoleBindingFlowSupport roleBindingFlowSupport = new RoleBindingFlowSupport();

    @Override
    public void process() {
        RegisterSourceFlowContext context = this.getContextBean(RegisterSourceFlowContext.class);
        roleBindingFlowSupport.resolveAndBind(context);
    }
}
