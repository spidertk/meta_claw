package com.meta_claw.knowledge.core;

import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.api.CoreController;
import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.api.req.SubmitWorkerJobRequest;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowExecutor;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.adapter.outbound.file.FileSnapshotStoreRepository;
import com.meta_claw.knowledge.core.adapter.outbound.file.FileSourceRegistryRepository;
import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.transport.worker.WorkerResultEnvelope;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Slf4j
/**
 * 本地演示入口。
 * 这里只负责装配 sample repository 和 flow facade，便于验证骨架链路，不代表最终运行时装配方式。
 */
public class CoreApplication {
    public static void main(String[] args) {
        Path demoStoreRoot = Path.of(System.getProperty("user.home"), "IdeaProjects", "meta_claw", "knowledge", "demo-store");
        SourceIntakeConfig sourceIntakeConfig = SourceIntakeConfig.defaultConfig();
        SampleKnowledgeSpaceBindingRepository knowledgeSpaceBindingRepository = new SampleKnowledgeSpaceBindingRepository();
        FileSourceRegistryRepository sourceRegistryRepository = new FileSourceRegistryRepository(demoStoreRoot.resolve("source-registry.jsonl"));
        FileSnapshotStoreRepository snapshotStoreRepository = new FileSnapshotStoreRepository(demoStoreRoot.resolve("snapshot-store.jsonl"));
        SampleKnowledgeStateRepository knowledgeStateRepository = new SampleKnowledgeStateRepository();
        KnowledgeFlowExecutor knowledgeFlowExecutor = new KnowledgeFlowExecutor();
        KnowledgeFlowFacade knowledgeFlowFacade = new KnowledgeFlowFacade(
                knowledgeFlowExecutor.getFlowExecutor(),
                knowledgeSpaceBindingRepository,
                sourceRegistryRepository,
                snapshotStoreRepository,
                knowledgeStateRepository,
                sourceIntakeConfig
        );

        CoreController controller = new CoreController(knowledgeFlowFacade);

        var firstRegistration = controller.registerSource(buildSampleRequest());
        log.info("First registration -> source={}, snapshot={}, latestSnapshotId={}, scanStatus={}, nextScanCursor={}, unchanged={}",
                firstRegistration.getSourceRecord().getSourceId(),
                firstRegistration.getSnapshotRecord().getSnapshotId(),
                firstRegistration.getSourceRecord().getLatestSnapshotId(),
                firstRegistration.getSnapshotRecord().getScanStatus(),
                firstRegistration.getSnapshotRecord().getNextScanCursor(),
                firstRegistration.isUnchanged());

        if ("partial".equals(firstRegistration.getSnapshotRecord().getScanStatus())) {
            var resumedSnapshot = controller.resumeSnapshotScan(firstRegistration.getSourceRecord().getSourceId());
            log.info("Resume scan -> snapshot={}, scanStatus={}, nextScanCursor={}, includedUnitCount={}",
                    resumedSnapshot.getSnapshotId(),
                    resumedSnapshot.getScanStatus(),
                    resumedSnapshot.getNextScanCursor(),
                    resumedSnapshot.getIncludedUnitCount());
        }

        var secondRegistration = controller.registerSource(buildSampleRequest());
        log.info("Second registration -> source={}, snapshot={}, latestSnapshotId={}, scanStatus={}, nextScanCursor={}, unchanged={}",
                secondRegistration.getSourceRecord().getSourceId(),
                secondRegistration.getSnapshotRecord().getSnapshotId(),
                secondRegistration.getSourceRecord().getLatestSnapshotId(),
                secondRegistration.getSnapshotRecord().getScanStatus(),
                secondRegistration.getSnapshotRecord().getNextScanCursor(),
                secondRegistration.isUnchanged());

        runWorkerDemo(controller, secondRegistration);
    }

    private static SourceRegistrationRequest buildSampleRequest() {
        return SourceRegistrationRequest.builder()
                .roleName("finance_advisor")
                .sourceType("git_repository")
                .location("/Users/kai/IdeaProjects/meta_claw")
                .displayName("meta_claw")
                .description("Sample source intake for Sprint 2.")
                .workspaceIdentity(com.meta_claw.knowledge.core.domain.SourceRecord.WorkspaceIdentity.builder()
                        .workspaceId("ws_meta_claw")
                        .workspaceRoot("/Users/kai/IdeaProjects/meta_claw")
                        .vcs("git")
                        .originMode("native_git")
                        .defaultBranch("main")
                        .build())
                .snapshotHint(com.meta_claw.knowledge.core.domain.SourceRecord.SnapshotHint.builder()
                        .branch("main")
                        .worktreeState("dirty")
                        .build())
                .build();
    }

    private static void runWorkerDemo(CoreController controller, com.meta_claw.knowledge.core.domain.SourceRegistrationResult registration) {
        SubmitWorkerJobRequest workerJobRequest = SubmitWorkerJobRequest.builder()
                .roleName("finance_advisor")
                .jobId("job_meta_claw_demo_001")
                .jobType("extract_graph")
                .sourceId(registration.getSourceRecord().getSourceId())
                .snapshotId(registration.getSnapshotRecord().getSnapshotId())
                .processingScope("latest_snapshot")
                .expectedArtifacts(List.of("graph", "wiki"))
                .build();

        var workerJob = controller.submitWorkerJob(workerJobRequest);
        log.info("Submit worker job -> jobId={}, spaceId={}, snapshotId={}, jobType={}",
                workerJob.getJobId(),
                workerJob.getSpaceId(),
                workerJob.getSnapshotId(),
                workerJob.getJobType());

        WorkerResultEnvelope workerResultEnvelope = WorkerResultEnvelope.builder()
                .spaceId(workerJob.getSpaceId())
                .jobId(workerJob.getJobId())
                .status("completed")
                .retriable(false)
                .artifacts(List.of(
                        KnowledgeAsset.builder()
                                .spaceId(workerJob.getSpaceId())
                                .assetId("asset_graph_meta_claw_demo_001")
                                .assetType("graph")
                                .sourceId(registration.getSourceRecord().getSourceId())
                                .snapshotId(workerJob.getSnapshotId())
                                .status("ready")
                                .coverage("partial")
                                .scope("latest_snapshot")
                                .createdAt(Instant.now())
                                .build()
                ))
                .issues(List.of())
                .coverage("partial")
                .scope("latest_snapshot")
                .build();

        var workerResult = controller.ingestWorkerResult(workerResultEnvelope);
        log.info("Ingest worker result -> jobId={}, status={}, coverage={}, artifactCount={}",
                workerResult.getJobId(),
                workerResult.getStatus(),
                workerResult.getCoverage(),
                workerResult.getArtifacts() == null ? 0 : workerResult.getArtifacts().size());
    }
}
