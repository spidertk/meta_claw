package com.meta_claw.knowledge.core.api.req;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.meta_claw.knowledge.core.domain.WorkerJob;

import java.util.List;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubmitWorkerJobRequest implements RoleScopedRequest {
    private String roleName;
    private String jobId;
    private String jobType;
    private String sourceId;
    private String snapshotId;
    private String processingScope;
    private List<String> expectedArtifacts;

    public WorkerJob toDomain(String spaceId) {
        return WorkerJob.builder()
                .spaceId(spaceId)
                .jobId(jobId)
                .jobType(jobType)
                .sourceId(sourceId)
                .snapshotId(snapshotId)
                .processingScope(processingScope)
                .expectedArtifacts(expectedArtifacts)
                .build();
    }
}
