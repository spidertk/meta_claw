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
public class WorkerJob {
    private String spaceId;
    private String jobId;
    private String jobType;
    private String sourceId;
    private String snapshotId;
    private String processingScope;
    private List<String> expectedArtifacts;
    private String analysisMode;
    private String sourceType;
    private String apiKey;
    private java.util.Map<String, Object> budgetHint;
}
