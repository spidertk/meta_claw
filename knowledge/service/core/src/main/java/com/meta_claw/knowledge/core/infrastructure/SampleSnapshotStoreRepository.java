package com.meta_claw.knowledge.core.infrastructure;

import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SnapshotStoreRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SampleSnapshotStoreRepository implements SnapshotStoreRepository {
    private final Map<String, SnapshotRecord> snapshots = new ConcurrentHashMap<>();

    @Override
    public void save(SnapshotRecord snapshotRecord) {
        snapshots.put(snapshotRecord.getSnapshotId(), snapshotRecord);
        log.debug("Saved snapshot {}", snapshotRecord.getSnapshotId());
    }

    @Override
    public Optional<SnapshotRecord> findById(String snapshotId) {
        return Optional.ofNullable(snapshots.get(snapshotId));
    }
}
