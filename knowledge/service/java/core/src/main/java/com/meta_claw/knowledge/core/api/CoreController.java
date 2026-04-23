package com.meta_claw.knowledge.core.api;

import com.meta_claw.knowledge.core.application.IngestWorkerResultUseCase;
import com.meta_claw.knowledge.core.application.RegisterSourceUseCase;
import com.meta_claw.knowledge.core.application.ResolveKnowledgeSpaceUseCase;
import com.meta_claw.knowledge.core.application.SubmitWorkerJobUseCase;
import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import com.meta_claw.knowledge.core.domain.WorkerResult;
import com.meta_claw.knowledge.core.transport.worker.WorkerResultEnvelope;

public class CoreController {
    private final ResolveKnowledgeSpaceUseCase resolveKnowledgeSpaceUseCase;
    private final RegisterSourceUseCase registerSourceUseCase;
    private final SubmitWorkerJobUseCase submitWorkerJobUseCase;
    private final IngestWorkerResultUseCase ingestWorkerResultUseCase;

    public CoreController(
            ResolveKnowledgeSpaceUseCase resolveKnowledgeSpaceUseCase,
            RegisterSourceUseCase registerSourceUseCase,
            SubmitWorkerJobUseCase submitWorkerJobUseCase,
            IngestWorkerResultUseCase ingestWorkerResultUseCase
    ) {
        this.resolveKnowledgeSpaceUseCase = resolveKnowledgeSpaceUseCase;
        this.registerSourceUseCase = registerSourceUseCase;
        this.submitWorkerJobUseCase = submitWorkerJobUseCase;
        this.ingestWorkerResultUseCase = ingestWorkerResultUseCase;
    }

    public AgentRoleBinding resolveKnowledgeSpace(String roleName) {
        return resolveKnowledgeSpaceUseCase.execute(roleName);
    }

    public SourceRecord registerSource(SourceRegistrationRequest request) {
        AgentRoleBinding binding = resolveKnowledgeSpaceUseCase.execute(request.roleName());
        return registerSourceUseCase.execute(request.toDomain(binding.spaceId()));
    }

    public WorkerJob submitWorkerJob(SubmitWorkerJobRequest request) {
        AgentRoleBinding binding = resolveKnowledgeSpaceUseCase.execute(request.roleName());
        return submitWorkerJobUseCase.execute(request.toDomain(binding.spaceId()));
    }

    public WorkerResult ingestWorkerResult(WorkerResultEnvelope envelope) {
        return ingestWorkerResultUseCase.execute(envelope.toDomain());
    }
}
