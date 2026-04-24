package com.meta_claw.knowledge.core.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.domain.KnowledgeControlState;
import com.meta_claw.knowledge.core.repository.KnowledgeStateRepository;
import com.meta_claw.knowledge.core.domain.WorkerResult;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class IngestWorkerResultService {
    private final KnowledgeStateRepository knowledgeStateRepository;

    public WorkerResult execute(WorkerResult workerResult) {
        for (KnowledgeAsset asset : workerResult.getArtifacts()) {
            knowledgeStateRepository.saveAsset(asset);
            knowledgeStateRepository.saveControlState(KnowledgeControlState.builder()
                    .spaceId(workerResult.getSpaceId())
                    .assetId(asset.getAssetId())
                    .reviewStatus(asset.getStatus())
                    .issues(workerResult.getIssues())
                    .updatedAt(Instant.now())
                    .build());
        }
        log.info("Ingested worker result {} for space {}", workerResult.getJobId(), workerResult.getSpaceId());
        return workerResult;
    }
}
