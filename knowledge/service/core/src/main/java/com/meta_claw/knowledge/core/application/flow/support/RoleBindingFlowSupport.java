package com.meta_claw.knowledge.core.application.flow.support;

import com.meta_claw.knowledge.core.application.flow.context.FlowRuntimeDependencies;
import com.meta_claw.knowledge.core.application.flow.context.RoleBindingFlowContext;
import com.meta_claw.knowledge.core.domain.AgentRoleBinding;

/**
 * 复用的 flow 绑定逻辑。
 * 负责从 role-bearing context 中解析并回填 knowledge space binding。
 */
public class RoleBindingFlowSupport {

    public void resolveAndBind(RoleBindingFlowContext context) {
        FlowRuntimeDependencies runtimeDependencies = context.getRuntimeDependencies();
        KnowledgeSpaceResolver knowledgeSpaceResolver = new KnowledgeSpaceResolver(
                runtimeDependencies.getKnowledgeSpaceBindingRepository()
        );
        AgentRoleBinding binding = knowledgeSpaceResolver.resolve(context.getRequest().getRoleName());
        context.setBinding(binding);
    }
}
