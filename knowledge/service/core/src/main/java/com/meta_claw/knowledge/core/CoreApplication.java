package com.meta_claw.knowledge.core;

import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.api.CoreController;
import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.application.IngestWorkerResultProcess;
import com.meta_claw.knowledge.core.application.RegisterSourceProcess;
import com.meta_claw.knowledge.core.application.ResolveKnowledgeSpaceBindingProcess;
import com.meta_claw.knowledge.core.application.SubmitWorkerJobProcess;
import com.meta_claw.knowledge.core.adapter.inbound.demo.SampleKnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.adapter.inbound.demo.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.adapter.inbound.demo.SampleSnapshotStoreRepository;
import com.meta_claw.knowledge.core.adapter.inbound.demo.SampleSourceRegistryRepository;

@Slf4j
/**
 * 本地演示入口。
 * 这里只负责装配 sample repository 和 process，便于验证骨架链路，不代表最终运行时装配方式。
 */
public class CoreApplication {
    public static void main(String[] args) {
        SampleKnowledgeSpaceBindingRepository knowledgeSpaceBindingRepository = new SampleKnowledgeSpaceBindingRepository();
        SampleSourceRegistryRepository sourceRegistryRepository = new SampleSourceRegistryRepository();
        SampleSnapshotStoreRepository snapshotStoreRepository = new SampleSnapshotStoreRepository();
        SampleKnowledgeStateRepository knowledgeStateRepository = new SampleKnowledgeStateRepository();

        CoreController controller = new CoreController(
                new ResolveKnowledgeSpaceBindingProcess(knowledgeSpaceBindingRepository),
                new RegisterSourceProcess(sourceRegistryRepository, snapshotStoreRepository),
                new SubmitWorkerJobProcess(),
                new IngestWorkerResultProcess(knowledgeStateRepository)
        );

        var firstRegistration = controller.registerSource(buildSampleRequest());
        log.info("First registration -> source={}, snapshot={}, latestSnapshotId={}, unchanged={}",
                firstRegistration.getSourceRecord().getSourceId(),
                firstRegistration.getSnapshotRecord().getSnapshotId(),
                firstRegistration.getSourceRecord().getLatestSnapshotId(),
                firstRegistration.isUnchanged());

        var secondRegistration = controller.registerSource(buildSampleRequest());
        log.info("Second registration -> source={}, snapshot={}, latestSnapshotId={}, unchanged={}",
                secondRegistration.getSourceRecord().getSourceId(),
                secondRegistration.getSnapshotRecord().getSnapshotId(),
                secondRegistration.getSourceRecord().getLatestSnapshotId(),
                secondRegistration.isUnchanged());
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
}
