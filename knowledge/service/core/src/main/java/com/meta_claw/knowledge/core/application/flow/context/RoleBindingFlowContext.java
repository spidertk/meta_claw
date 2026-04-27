package com.meta_claw.knowledge.core.application.flow.context;

import com.meta_claw.knowledge.core.api.req.RoleScopedRequest;
import com.meta_claw.knowledge.core.domain.AgentRoleBinding;

/**
 * 需要执行 role -> knowledge space 绑定的 flow context 协议。
 * 只暴露复用绑定逻辑所需的最小能力。
 */
public interface RoleBindingFlowContext {
    RoleScopedRequest getRequest();

    AgentRoleBinding getBinding();

    void setBinding(AgentRoleBinding binding);

    FlowRuntimeDependencies getRuntimeDependencies();
}
