package com.meta_claw.knowledge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

/**
 * 来源在某次采集时刻生成的不可变快照。
 * 每次内容变化都通过新增快照表达，而不是覆盖旧快照。
 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotRecord {
    /** 快照所属知识空间标识。 */
    private String spaceId;
    /** 本次快照的唯一标识。 */
    private String snapshotId;
    /** 该快照归属的来源标识。 */
    private String sourceId;
    /** 本次快照的内容指纹，用于判定来源是否变化。 */
    private String contentFingerprint;
    /** 本次快照的采集时间。 */
    private Instant capturedAt;
    /** 本次快照下的可追踪内容单元引用。 */
    private List<UnitRef> units;
}
