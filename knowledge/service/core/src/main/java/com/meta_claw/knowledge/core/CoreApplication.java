package com.meta_claw.knowledge.core;

import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.api.CoreController;
import com.meta_claw.knowledge.core.api.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.application.IngestWorkerResultUseCase;
import com.meta_claw.knowledge.core.application.RegisterSourceUseCase;
import com.meta_claw.knowledge.core.application.ResolveKnowledgeSpaceUseCase;
import com.meta_claw.knowledge.core.application.SubmitWorkerJobUseCase;
import com.meta_claw.knowledge.core.infrastructure.SampleKnowledgeRegistryRepository;
import com.meta_claw.knowledge.core.infrastructure.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.infrastructure.SampleSnapshotStoreRepository;
import com.meta_claw.knowledge.core.infrastructure.SampleSourceRegistryRepository;

@Slf4j
public class CoreApplication {
    public static void main(String[] args) {
        SampleKnowledgeRegistryRepository knowledgeRegistryRepository = new SampleKnowledgeRegistryRepository();
        SampleSourceRegistryRepository sourceRegistryRepository = new SampleSourceRegistryRepository();
        SampleSnapshotStoreRepository snapshotStoreRepository = new SampleSnapshotStoreRepository();
        SampleKnowledgeStateRepository knowledgeStateRepository = new SampleKnowledgeStateRepository();

        CoreController controller = new CoreController(
                new ResolveKnowledgeSpaceUseCase(knowledgeRegistryRepository),
                new RegisterSourceUseCase(sourceRegistryRepository, snapshotStoreRepository),
                new SubmitWorkerJobUseCase(),
                new IngestWorkerResultUseCase(knowledgeStateRepository)
        );

        var result = controller.registerSource(SourceRegistrationRequest.builder()
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
                .build());

        log.info("Registered source {} with snapshot {}",
                result.getSourceRecord().getSourceId(),
                result.getSnapshotRecord().getSnapshotId());
    }
}
