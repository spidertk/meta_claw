package com.meta_claw.knowledge.core.repository;

import com.meta_claw.knowledge.core.domain.SnapshotRecord;

import java.util.Optional;

/**
 * 快照历史存储。
 * 只负责保存来源的不可变快照历史，不拥有来源当前状态定义权。
 */
public interface SnapshotStoreRepository {
    /** 追加保存一次快照历史记录。 */
    void save(SnapshotRecord snapshotRecord);

    /** 按快照标识读取历史快照。 */
    Optional<SnapshotRecord> findById(String snapshotId);

    /** 按来源读取最新快照，用于变化判定和复用现有快照。 */
    Optional<SnapshotRecord> findLatestBySourceId(String sourceId);
}
