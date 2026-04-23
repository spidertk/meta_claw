package com.meta_claw.knowledge.core.api;

import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.domain.WorkerResult;

import java.util.List;

public record WorkerResultRequest(
        String spaceId,
        String jobId,
        String status,
        boolean retriable,
        List<KnowledgeAsset> artifacts,
        List<String> issues,
        String coverage,
        String scope
) {
    public WorkerResult toDomain() {
        return new WorkerResult(spaceId, jobId, status, retriable, artifacts, issues, coverage, scope);
    }
}
