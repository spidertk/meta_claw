package com.meta_claw.knowledge.core.application;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.domain.SourceRecord;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SourceRegistrationResult {
    private SourceRecord sourceRecord;
    private SnapshotRecord snapshotRecord;
    private boolean unchanged;
}
