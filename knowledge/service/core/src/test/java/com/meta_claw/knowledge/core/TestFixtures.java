package com.meta_claw.knowledge.core;

import com.meta_claw.knowledge.core.api.req.SourceRegistrationRequest;
import com.meta_claw.knowledge.core.domain.SourceRecord;

public final class TestFixtures {

    private TestFixtures() {}

    public static SourceRegistrationRequest sampleRepoRequest() {
        return SourceRegistrationRequest.builder()
                .roleName("test_role")
                .sourceType("git_repository")
                .location("src/test/resources/samples/sample-repo")
                .displayName("sample_repo")
                .description("Test fixture repository")
                .workspaceIdentity(SourceRecord.WorkspaceIdentity.builder()
                        .workspaceId("ws_sample")
                        .workspaceRoot("src/test/resources/samples/sample-repo")
                        .vcs("git")
                        .originMode("native_git")
                        .defaultBranch("main")
                        .build())
                .snapshotHint(SourceRecord.SnapshotHint.builder()
                        .branch("main")
                        .worktreeState("clean")
                        .build())
                .build();
    }
}
