package com.meta_claw.knowledge.core.repository;

import com.meta_claw.knowledge.core.domain.SourceRecord;

import java.util.Optional;

/**
 * 来源注册表。
 * 只负责来源稳定身份、当前状态和 latestSnapshotId 派生指针，
 * 不负责保存完整快照历史。
 */
public interface SourceRegistryRepository {
    /**
     * 保存来源当前视图。
     * 应用层需要自行保证它与 snapshot_store 的协同写入一致性。
     */
    void save(SourceRecord sourceRecord);

    /** 按来源稳定标识读取当前视图。 */
    Optional<SourceRecord> findById(String sourceId);
}
