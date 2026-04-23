package com.meta_claw.knowledge.core.domain;

import java.util.List;

public record UnitRef(
        String unitId,
        String snapshotId,
        String parentUnitId,
        String unitType,
        String path,
        String displayName,
        List<String> neighbors
) {
}
