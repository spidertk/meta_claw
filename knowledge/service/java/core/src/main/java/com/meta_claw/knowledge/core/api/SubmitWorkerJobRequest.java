package com.meta_claw.knowledge.core.api;

import com.meta_claw.knowledge.core.domain.WorkerJob;

import java.util.List;

public record SubmitWorkerJobRequest(
        String roleName,
        String jobId,
        String jobType,
        String sourceId,
        String snapshotId,
        String processingScope,
        List<String> expectedArtifacts
) {
    public WorkerJob toDomain(String spaceId) {
        return new WorkerJob(spaceId, jobId, jobType, sourceId, snapshotId, processingScope, expectedArtifacts);
    }
}
