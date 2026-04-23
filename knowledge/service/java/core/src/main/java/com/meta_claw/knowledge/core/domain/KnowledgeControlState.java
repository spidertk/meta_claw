package com.meta_claw.knowledge.core.domain;

import java.time.Instant;
import java.util.List;

public record KnowledgeControlState(
        String spaceId,
        String assetId,
        String reviewStatus,
        List<String> issues,
        Instant updatedAt
) {
}
