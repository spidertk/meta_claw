package com.meta_claw.knowledge.core.domain;

import java.util.List;

public record WorkerJob(
        String spaceId,
        String jobId,
        String jobType,
        String sourceId,
        String snapshotId,
        String processingScope,
        List<String> expectedArtifacts
) {
}
