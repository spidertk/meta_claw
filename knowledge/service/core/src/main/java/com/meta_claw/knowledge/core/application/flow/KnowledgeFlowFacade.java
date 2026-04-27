package com.meta_claw.knowledge.core.application.flow;

import com.meta_claw.knowledge.core.application.flow.context.IngestWorkerResultFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.ResumeSnapshotScanFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.SubmitWorkerJobFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.FlowRuntimeDependencies;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import com.meta_claw.knowledge.core.domain.WorkerResult;
import com.meta_claw.knowledge.core.repository.KnowledgeStateRepository;
import com.meta_claw.knowledge.core.repository.KnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.repository.SnapshotStoreRepository;
import com.meta_claw.knowledge.core.repository.SourceRegistryRepository;
import com.yomahub.liteflow.core.FlowExecutor;
import com.yomahub.liteflow.flow.LiteflowResponse;

public class KnowledgeFlowFacade {

    private final FlowExecutor flowExecutor;
    private final KnowledgeSpaceBindingRepository knowledgeSpaceBindingRepository;
    private final SourceRegistryRepository sourceRegistryRepository;
    private final SnapshotStoreRepository snapshotStoreRepository;
    private final KnowledgeStateRepository knowledgeStateRepository;
    private final SourceIntakeConfig sourceIntakeConfig;

    public KnowledgeFlowFacade(
            FlowExecutor flowExecutor,
            KnowledgeSpaceBindingRepository knowledgeSpaceBindingRepository,
            SourceRegistryRepository sourceRegistryRepository,
            SnapshotStoreRepository snapshotStoreRepository,
            SourceIntakeConfig sourceIntakeConfig
    ) {
        this(
                flowExecutor,
                knowledgeSpaceBindingRepository,
                sourceRegistryRepository,
                snapshotStoreRepository,
                null,
                sourceIntakeConfig
        );
    }

    public KnowledgeFlowFacade(
            FlowExecutor flowExecutor,
            KnowledgeSpaceBindingRepository knowledgeSpaceBindingRepository,
            SourceRegistryRepository sourceRegistryRepository,
            SnapshotStoreRepository snapshotStoreRepository,
            KnowledgeStateRepository knowledgeStateRepository,
            SourceIntakeConfig sourceIntakeConfig
    ) {
        this.flowExecutor = flowExecutor;
        this.knowledgeSpaceBindingRepository = knowledgeSpaceBindingRepository;
        this.sourceRegistryRepository = sourceRegistryRepository;
        this.snapshotStoreRepository = snapshotStoreRepository;
        this.knowledgeStateRepository = knowledgeStateRepository;
        this.sourceIntakeConfig = sourceIntakeConfig;
    }

    public SourceRegistrationResult registerSource(RegisterSourceFlowContext context) {
        RegisterSourceFlowContext runtimeContext = context.toBuilder()
                .runtimeDependencies(buildRuntimeDependencies())
                .build();
        RegisterSourceFlowContext resultContext = execute("registerSourceChain", runtimeContext, RegisterSourceFlowContext.class);
        return resultContext.getResult();
    }

    public SnapshotRecord resumeSnapshotScan(ResumeSnapshotScanFlowContext context) {
        ResumeSnapshotScanFlowContext resultContext = execute(
                "resumeSnapshotScanChain",
                context.toBuilder()
                        .runtimeDependencies(buildRuntimeDependencies())
                        .build(),
                ResumeSnapshotScanFlowContext.class
        );
        return resultContext.getResultSnapshot();
    }

    public WorkerJob submitWorkerJob(SubmitWorkerJobFlowContext context) {
        SubmitWorkerJobFlowContext resultContext = execute(
                "submitWorkerJobChain",
                context.toBuilder()
                        .runtimeDependencies(buildRuntimeDependencies())
                        .build(),
                SubmitWorkerJobFlowContext.class
        );
        return resultContext.getResult();
    }

    public WorkerResult ingestWorkerResult(IngestWorkerResultFlowContext context) {
        IngestWorkerResultFlowContext resultContext = execute(
                "ingestWorkerResultChain",
                context.toBuilder()
                        .runtimeDependencies(buildRuntimeDependencies())
                        .build(),
                IngestWorkerResultFlowContext.class
        );
        return resultContext.getResult();
    }

    private FlowRuntimeDependencies buildRuntimeDependencies() {
        return FlowRuntimeDependencies.builder()
                .knowledgeSpaceBindingRepository(knowledgeSpaceBindingRepository)
                .sourceRegistryRepository(sourceRegistryRepository)
                .snapshotStoreRepository(snapshotStoreRepository)
                .knowledgeStateRepository(knowledgeStateRepository)
                .sourceIntakeConfig(sourceIntakeConfig)
                .build();
    }

    private <T> T execute(String chainId, T context, Class<T> contextType) {
        LiteflowResponse response = flowExecutor.execute2Resp(chainId, null, context);
        if (!response.isSuccess()) {
            throw new IllegalStateException(chainId + " failed", response.getCause());
        }
        return response.getContextBean(contextType);
    }
}
