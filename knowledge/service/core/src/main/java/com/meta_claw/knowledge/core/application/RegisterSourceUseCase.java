package com.meta_claw.knowledge.core.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SnapshotStoreRepository;
import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.meta_claw.knowledge.core.domain.SourceRegistryRepository;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class RegisterSourceUseCase {
    private final SourceRegistryRepository sourceRegistryRepository;
    private final SnapshotStoreRepository snapshotStoreRepository;

    public SourceRegistrationResult execute(SourceRecord sourceRecord) {
        String sourceId = SourceIntakeSupport.ensureSourceId(sourceRecord);
        Instant now = Instant.now();
        SourceRecord resolvedSource = SourceRecord.builder()
                .spaceId(sourceRecord.getSpaceId())
                .sourceId(sourceId)
                .sourceType(sourceRecord.getSourceType())
                .location(sourceRecord.getLocation())
                .displayName(sourceRecord.getDisplayName())
                .status(sourceRecord.getStatus())
                .description(sourceRecord.getDescription())
                .workspaceIdentity(sourceRecord.getWorkspaceIdentity())
                .snapshotHint(sourceRecord.getSnapshotHint())
                .createdAt(sourceRecord.getCreatedAt() != null ? sourceRecord.getCreatedAt() : now)
                .updatedAt(now)
                .build();

        SnapshotRecord snapshotRecord = SourceIntakeSupport.createSnapshot(resolvedSource);
        sourceRegistryRepository.save(resolvedSource);
        snapshotStoreRepository.save(snapshotRecord);
        log.info("Registered source {} in space {} with snapshot {}",
                resolvedSource.getSourceId(), resolvedSource.getSpaceId(), snapshotRecord.getSnapshotId());
        return SourceRegistrationResult.builder()
                .sourceRecord(resolvedSource)
                .snapshotRecord(snapshotRecord)
                .build();
    }
}
