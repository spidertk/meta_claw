package com.meta_claw.knowledge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/** 来源注册流程的返回结果。 */
@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SourceRegistrationResult {
    /** 注册完成后的来源当前视图。 */
    private SourceRecord sourceRecord;
    /** 本次流程复用或新生成的快照。 */
    private SnapshotRecord snapshotRecord;
    /** 是否复用了已有最新快照。 */
    private boolean unchanged;
}
