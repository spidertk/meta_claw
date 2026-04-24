package com.meta_claw.knowledge.core.api.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.meta_claw.knowledge.core.domain.SourceRecord;

import java.time.Instant;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SourceRegistrationRequest {
    private String roleName;
    private String sourceId;
    private String sourceType;
    private String location;
    private String displayName;
    private String status;
    private String description;
    private SourceRecord.WorkspaceIdentity workspaceIdentity;
    private SourceRecord.SnapshotHint snapshotHint;

    public SourceRecord toDomain(String spaceId) {
        Instant now = Instant.now();
        return SourceRecord.builder()
                .spaceId(spaceId)
                .sourceId(sourceId)
                .sourceType(sourceType)
                .location(location)
                .displayName(displayName)
                .status(status == null || status.isBlank() ? "new_source" : status)
                .description(description)
                .workspaceIdentity(workspaceIdentity)
                .snapshotHint(snapshotHint)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
