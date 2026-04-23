package com.meta_claw.knowledge.core.domain;

import java.time.Instant;

public record KnowledgeAsset(
        String spaceId,
        String assetId,
        String assetType,
        String sourceId,
        String snapshotId,
        String status,
        String coverage,
        String scope,
        Instant createdAt
) {
}
