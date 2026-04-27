package com.meta_claw.knowledge.core.application.flow.context;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RegisterSourceFlowContext implements RoleBindingFlowContext {

    private SourceRegistrationRequest request;
    private AgentRoleBinding binding;
    private SourceRecord incomingSourceRecord;
    private SourceRecord resolvedSourceRecord;
    private SnapshotRecord latestSnapshot;
    private SnapshotRecord candidateSnapshot;
    private SourceRegistrationResult result;
    private Boolean unchanged;
    private FlowRuntimeDependencies runtimeDependencies;
}
