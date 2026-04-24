package com.meta_claw.knowledge.core.service;

import com.meta_claw.knowledge.core.domain.*;
import com.meta_claw.knowledge.core.repository.SnapshotStoreRepository;
import com.meta_claw.knowledge.core.repository.SourceRegistryRepository;
import com.meta_claw.knowledge.core.util.SourceIntakeSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class SourceRegisterService {
    private final SourceRegistryRepository sourceRegistryRepository;
    private final SnapshotStoreRepository snapshotStoreRepository;

    public SourceRegistrationResult execute(SourceRecord sourceRecord) {
        String sourceId = SourceIntakeSupport.ensureSourceId(sourceRecord);
        Instant now = Instant.now();
        Optional<SourceRecord> existingSource = sourceRegistryRepository.findById(sourceId);
        SourceRecord resolvedSource = SourceRecord.builder()
                .spaceId(sourceRecord.getSpaceId())
                .sourceId(sourceId)
                .sourceType(sourceRecord.getSourceType())
                .location(sourceRecord.getLocation())
                .displayName(sourceRecord.getDisplayName())
                .status(existingSource.isPresent() ? "partial_update" : sourceRecord.getStatus())
                .description(sourceRecord.getDescription())
                .workspaceIdentity(sourceRecord.getWorkspaceIdentity())
                .snapshotHint(sourceRecord.getSnapshotHint())
                .createdAt(existingSource.map(SourceRecord::getCreatedAt).orElse(sourceRecord.getCreatedAt() != null ? sourceRecord.getCreatedAt() : now))
                .updatedAt(now)
                .build();

        SnapshotRecord snapshotRecord = SourceIntakeSupport.createSnapshot(resolvedSource);
        Optional<SnapshotRecord> latestSnapshot = snapshotStoreRepository.findLatestBySourceId(sourceId);
        boolean unchanged = latestSnapshot
                .map(existing -> existing.getContentFingerprint().equals(snapshotRecord.getContentFingerprint()))
                .orElse(false);

        if (unchanged) {
            resolvedSource.setStatus("unchanged");
            sourceRegistryRepository.save(resolvedSource);
            SnapshotRecord reusedSnapshot = latestSnapshot.orElse(snapshotRecord);
            log.info("Source {} in space {} is unchanged; reusing snapshot {}",
                    resolvedSource.getSourceId(), resolvedSource.getSpaceId(), reusedSnapshot.getSnapshotId());
            return SourceRegistrationResult.builder()
                    .sourceRecord(resolvedSource)
                    .snapshotRecord(reusedSnapshot)
                    .unchanged(true)
                    .build();
        }

        sourceRegistryRepository.save(resolvedSource);
        snapshotStoreRepository.save(snapshotRecord);
        log.info("Registered source {} in space {} with snapshot {}",
                resolvedSource.getSourceId(), resolvedSource.getSpaceId(), snapshotRecord.getSnapshotId());
        return SourceRegistrationResult.builder()
                .sourceRecord(resolvedSource)
                .snapshotRecord(snapshotRecord)
                .unchanged(false)
                .build();
    }
}
