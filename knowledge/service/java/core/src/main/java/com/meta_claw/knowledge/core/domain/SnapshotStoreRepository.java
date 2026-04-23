package com.meta_claw.knowledge.core.domain;

import java.util.Optional;

public interface SnapshotStoreRepository {
    void save(SnapshotRecord snapshotRecord);

    Optional<SnapshotRecord> findById(String snapshotId);
}
