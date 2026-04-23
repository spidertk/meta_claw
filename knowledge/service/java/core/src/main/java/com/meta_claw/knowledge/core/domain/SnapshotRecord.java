package com.meta_claw.knowledge.core.domain;

import java.time.Instant;
import java.util.List;

public record SnapshotRecord(
        String spaceId,
        String snapshotId,
        String sourceId,
        String contentFingerprint,
        Instant capturedAt,
        List<UnitRef> units
) {
}
