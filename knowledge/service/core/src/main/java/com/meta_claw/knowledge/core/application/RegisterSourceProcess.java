package com.meta_claw.knowledge.core.application;

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
/**
 * 应用层流程编排器：注册来源，并协调 source_registry 与 snapshot_store 的双写。
 */
public class RegisterSourceProcess {
    private final SourceRegistryRepository sourceRegistryRepository;
    private final SnapshotStoreRepository snapshotStoreRepository;

    /**
     * 执行来源注册流程。
     * 变化时生成并保存新快照，同时回写 latestSnapshotId；
     * 未变化时复用已有快照，只更新来源当前视图。
     */
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
                .latestSnapshotId(existingSource.map(SourceRecord::getLatestSnapshotId).orElse(null))
                .build();

        SnapshotRecord snapshotRecord = SourceIntakeSupport.createSnapshot(resolvedSource);
        Optional<SnapshotRecord> latestSnapshot = snapshotStoreRepository.findLatestBySourceId(sourceId);
        boolean unchanged = latestSnapshot
                .map(existing -> existing.getContentFingerprint().equals(snapshotRecord.getContentFingerprint()))
                .orElse(false);

        if (unchanged) {
            SnapshotRecord reusedSnapshot = latestSnapshot.orElse(snapshotRecord);
            resolvedSource.setStatus("unchanged");
            resolvedSource.setLatestSnapshotId(reusedSnapshot.getSnapshotId());
            sourceRegistryRepository.save(resolvedSource);
            log.info("Source {} in space {} is unchanged; reusing snapshot {}",
                    resolvedSource.getSourceId(), resolvedSource.getSpaceId(), reusedSnapshot.getSnapshotId());
            return SourceRegistrationResult.builder()
                    .sourceRecord(resolvedSource)
                    .snapshotRecord(reusedSnapshot)
                    .unchanged(true)
                    .build();
        }

        resolvedSource.setLatestSnapshotId(snapshotRecord.getSnapshotId());
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
