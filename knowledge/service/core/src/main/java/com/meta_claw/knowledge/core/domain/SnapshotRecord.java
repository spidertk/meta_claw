package com.meta_claw.knowledge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotRecord {
    private String spaceId;
    private String snapshotId;
    private String sourceId;
    private String contentFingerprint;
    private Instant capturedAt;
    private List<UnitRef> units;
}
