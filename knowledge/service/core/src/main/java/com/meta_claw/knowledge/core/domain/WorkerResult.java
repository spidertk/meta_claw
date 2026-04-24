package com.meta_claw.knowledge.core.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WorkerResult {
    private String spaceId;
    private String jobId;
    private String status;
    private boolean retriable;
    private List<KnowledgeAsset> artifacts;
    private List<String> issues;
    private String coverage;
    private String scope;
}
