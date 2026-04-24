package com.meta_claw.knowledge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeAsset {
    private String spaceId;
    private String assetId;
    private String assetType;
    private String sourceId;
    private String snapshotId;
    private String status;
    private String coverage;
    private String scope;
    private Instant createdAt;
}
