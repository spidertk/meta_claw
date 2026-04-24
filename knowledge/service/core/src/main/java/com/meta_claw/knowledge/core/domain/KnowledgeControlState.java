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
public class KnowledgeControlState {
    private String spaceId;
    private String assetId;
    private String reviewStatus;
    private List<String> issues;
    private Instant updatedAt;
}
