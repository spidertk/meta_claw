package com.meta_claw.knowledge.core.application;

import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.domain.KnowledgeControlState;
import com.meta_claw.knowledge.core.domain.KnowledgeStateRepository;
import com.meta_claw.knowledge.core.domain.WorkerResult;

import java.time.Instant;

public class IngestWorkerResultUseCase {
    private final KnowledgeStateRepository knowledgeStateRepository;

    public IngestWorkerResultUseCase(KnowledgeStateRepository knowledgeStateRepository) {
        this.knowledgeStateRepository = knowledgeStateRepository;
    }

    public WorkerResult execute(WorkerResult workerResult) {
        for (KnowledgeAsset asset : workerResult.artifacts()) {
            knowledgeStateRepository.saveAsset(asset);
            knowledgeStateRepository.saveControlState(new KnowledgeControlState(
                    workerResult.spaceId(),
                    asset.assetId(),
                    asset.status(),
                    workerResult.issues(),
                    Instant.now()
            ));
        }
        return workerResult;
    }
}
