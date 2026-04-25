package com.meta_claw.knowledge.core.api;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.api.req.SubmitWorkerJobRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.application.IngestWorkerResultProcess;
import com.meta_claw.knowledge.core.application.RegisterSourceProcess;
import com.meta_claw.knowledge.core.application.ResolveKnowledgeSpaceBindingProcess;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import com.meta_claw.knowledge.core.application.SubmitWorkerJobProcess;
import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import com.meta_claw.knowledge.core.domain.WorkerResult;
import com.meta_claw.knowledge.core.transport.worker.WorkerResultEnvelope;

@Slf4j
@RequiredArgsConstructor
/**
 * Core 对外控制入口。
 * 负责把外部请求分派到应用层 process，而不是直接承载业务流程细节。
 */
public class CoreController {
    private final ResolveKnowledgeSpaceBindingProcess resolveKnowledgeSpaceBindingProcess;
    private final RegisterSourceProcess registerSourceProcess;
    private final SubmitWorkerJobProcess submitWorkerJobProcess;
    private final IngestWorkerResultProcess ingestWorkerResultProcess;

    public AgentRoleBinding resolveKnowledgeSpace(String roleName) {
        return resolveKnowledgeSpaceBindingProcess.execute(roleName);
    }

    public SourceRegistrationResult registerSource(SourceRegistrationRequest request) {
        AgentRoleBinding binding = resolveKnowledgeSpaceBindingProcess.execute(request.getRoleName());
        log.debug("Registering source for role {} in space {}", request.getRoleName(), binding.getSpaceId());
        return registerSourceProcess.execute(request.toDomain(binding.getSpaceId()));
    }

    public WorkerJob submitWorkerJob(SubmitWorkerJobRequest request) {
        AgentRoleBinding binding = resolveKnowledgeSpaceBindingProcess.execute(request.getRoleName());
        return submitWorkerJobProcess.execute(request.toDomain(binding.getSpaceId()));
    }

    public WorkerResult ingestWorkerResult(WorkerResultEnvelope envelope) {
        return ingestWorkerResultProcess.execute(envelope.toDomain());
    }
}
