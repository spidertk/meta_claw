package com.meta_claw.knowledge.core.adapter.outbound.demo;

import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.repository.SnapshotStoreRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
/**
 * 演示用快照历史存储实现。
 * 仅用于本地内存示例，快照一旦写入就按历史记录对待。
 */
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

    @Override
    public Optional<SnapshotRecord> findLatestBySourceId(String sourceId) {
        return snapshots.values().stream()
                .filter(snapshot -> snapshot.getSourceId().equals(sourceId))
                .max((left, right) -> left.getCapturedAt().compareTo(right.getCapturedAt()));
    }
}
