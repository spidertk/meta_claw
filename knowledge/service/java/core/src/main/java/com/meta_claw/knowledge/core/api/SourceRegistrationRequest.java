package com.meta_claw.knowledge.core.api;

import com.meta_claw.knowledge.core.domain.SourceRecord;

import java.time.Instant;

public record SourceRegistrationRequest(
        String roleName,
        String sourceId,
        String sourceType,
        String location,
        String displayName,
        String status,
        String description,
        SourceRecord.WorkspaceIdentity workspaceIdentity,
        SourceRecord.SnapshotHint snapshotHint
) {
    public SourceRecord toDomain(String spaceId) {
        Instant now = Instant.now();
        return new SourceRecord(
                spaceId,
                sourceId,
                sourceType,
                location,
                displayName,
                status,
                description,
                workspaceIdentity,
                snapshotHint,
                now,
                now
        );
    }
}
