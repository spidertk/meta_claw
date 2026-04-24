package com.meta_claw.knowledge.core.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.application.IngestWorkerResultUseCase;
import com.meta_claw.knowledge.core.application.RegisterSourceUseCase;
import com.meta_claw.knowledge.core.application.ResolveKnowledgeSpaceUseCase;
import com.meta_claw.knowledge.core.application.SourceRegistrationResult;
import com.meta_claw.knowledge.core.application.SubmitWorkerJobUseCase;
import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import com.meta_claw.knowledge.core.domain.WorkerResult;
import com.meta_claw.knowledge.core.transport.worker.WorkerResultEnvelope;

@Slf4j
@RequiredArgsConstructor
public class CoreController {
    private final ResolveKnowledgeSpaceUseCase resolveKnowledgeSpaceUseCase;
    private final RegisterSourceUseCase registerSourceUseCase;
    private final SubmitWorkerJobUseCase submitWorkerJobUseCase;
    private final IngestWorkerResultUseCase ingestWorkerResultUseCase;

    public AgentRoleBinding resolveKnowledgeSpace(String roleName) {
        return resolveKnowledgeSpaceUseCase.execute(roleName);
    }

    public SourceRegistrationResult registerSource(SourceRegistrationRequest request) {
        AgentRoleBinding binding = resolveKnowledgeSpaceUseCase.execute(request.getRoleName());
        log.debug("Registering source for role {} in space {}", request.getRoleName(), binding.getSpaceId());
        return registerSourceUseCase.execute(request.toDomain(binding.getSpaceId()));
    }

    public WorkerJob submitWorkerJob(SubmitWorkerJobRequest request) {
        AgentRoleBinding binding = resolveKnowledgeSpaceUseCase.execute(request.getRoleName());
        return submitWorkerJobUseCase.execute(request.toDomain(binding.getSpaceId()));
    }

    public WorkerResult ingestWorkerResult(WorkerResultEnvelope envelope) {
        return ingestWorkerResultUseCase.execute(envelope.toDomain());
    }
}
