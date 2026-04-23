package com.meta_claw.knowledge.core.domain;

import java.time.Instant;

public record SourceRecord(
        String spaceId,
        String sourceId,
        String sourceType,
        String location,
        String displayName,
        String status,
        String description,
        WorkspaceIdentity workspaceIdentity,
        SnapshotHint snapshotHint,
        Instant createdAt,
        Instant updatedAt
) {
    public record WorkspaceIdentity(
            String workspaceId,
            String workspaceRoot,
            String vcs,
            String originMode,
            String remoteUrl,
            String defaultBranch
    ) {
    }

    public record SnapshotHint(
            String branch,
            String headCommit,
            String worktreeState
    ) {
    }
}
