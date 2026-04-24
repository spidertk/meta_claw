package com.meta_claw.knowledge.core.transport.worker;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.domain.WorkerResult;

import java.util.List;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class WorkerResultEnvelope {
    private String spaceId;
    private String jobId;
    private String status;
    private boolean retriable;
    private List<KnowledgeAsset> artifacts;
    private List<String> issues;
    private String coverage;
    private String scope;

    public WorkerResult toDomain() {
        return WorkerResult.builder()
                .spaceId(spaceId)
                .jobId(jobId)
                .status(status)
                .retriable(retriable)
                .artifacts(artifacts)
                .issues(issues)
                .coverage(coverage)
                .scope(scope)
                .build();
    }
}
