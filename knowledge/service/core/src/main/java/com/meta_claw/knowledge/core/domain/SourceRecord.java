package com.meta_claw.knowledge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SourceRecord {
    private String spaceId;
    private String sourceId;
    private String sourceType;
    private String location;
    private String displayName;
    private String status;
    private String description;
    private WorkspaceIdentity workspaceIdentity;
    private SnapshotHint snapshotHint;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkspaceIdentity {
        private String workspaceId;
        private String workspaceRoot;
        private String vcs;
        private String originMode;
        private String remoteUrl;
        private String defaultBranch;
    }

    @Data
    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotHint {
        private String branch;
        private String headCommit;
        private String worktreeState;
    }
}
