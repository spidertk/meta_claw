package com.meta_claw.knowledge.core.domain;

import java.util.List;

public record WorkerResult(
        String spaceId,
        String jobId,
        String status,
        boolean retriable,
        List<KnowledgeAsset> artifacts,
        List<String> issues,
        String coverage,
        String scope
) {
}
