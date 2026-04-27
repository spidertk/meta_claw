package com.meta_claw.knowledge.core.api;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.api.req.SubmitWorkerJobRequest;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.flow.context.IngestWorkerResultFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.ResumeSnapshotScanFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.SubmitWorkerJobFlowContext;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import com.meta_claw.knowledge.core.domain.WorkerResult;
import com.meta_claw.knowledge.core.transport.worker.WorkerResultEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
/**
 * Core 对外控制入口。
 * 负责把外部请求分派到 LiteFlow facade，而不是直接承载业务流程细节。
 */
public class CoreController {
    private final KnowledgeFlowFacade knowledgeFlowFacade;

    public SourceRegistrationResult registerSource(SourceRegistrationRequest request) {
        log.debug("Registering source for role {}", request.getRoleName());
        return knowledgeFlowFacade.registerSource(
                RegisterSourceFlowContext.builder()
                        .request(request)
                        .build()
        );
    }

    public SnapshotRecord resumeSnapshotScan(String sourceId) {
        return knowledgeFlowFacade.resumeSnapshotScan(
                ResumeSnapshotScanFlowContext.builder()
                        .sourceId(sourceId)
                        .build()
        );
    }

    public WorkerJob submitWorkerJob(SubmitWorkerJobRequest request) {
        return knowledgeFlowFacade.submitWorkerJob(
                SubmitWorkerJobFlowContext.builder()
                        .request(request)
                        .build()
        );
    }

    public WorkerResult ingestWorkerResult(WorkerResultEnvelope envelope) {
        return knowledgeFlowFacade.ingestWorkerResult(
                IngestWorkerResultFlowContext.builder()
                        .envelope(envelope)
                        .build()
        );
    }
}
