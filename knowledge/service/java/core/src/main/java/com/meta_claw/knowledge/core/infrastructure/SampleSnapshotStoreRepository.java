package com.meta_claw.knowledge.core.infrastructure;

import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SnapshotStoreRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SampleSnapshotStoreRepository implements SnapshotStoreRepository {
    private final Map<String, SnapshotRecord> snapshots = new ConcurrentHashMap<>();

    @Override
    public void save(SnapshotRecord snapshotRecord) {
        snapshots.put(snapshotRecord.snapshotId(), snapshotRecord);
    }

    @Override
    public Optional<SnapshotRecord> findById(String snapshotId) {
        return Optional.ofNullable(snapshots.get(snapshotId));
    }
}
