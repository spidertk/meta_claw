package com.meta_claw.knowledge.core.application.flow.context;

import com.meta_claw.knowledge.core.api.req.SubmitWorkerJobRequest;
import com.meta_claw.knowledge.core.application.flow.context.FlowRuntimeDependencies;
import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubmitWorkerJobFlowContext implements RoleBindingFlowContext {

    private SubmitWorkerJobRequest request;
    private AgentRoleBinding binding;
    private WorkerJob workerJob;
    private WorkerJob result;
    private FlowRuntimeDependencies runtimeDependencies;
}
