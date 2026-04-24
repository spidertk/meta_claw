package com.meta_claw.knowledge.core.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.domain.KnowledgeControlState;
import com.meta_claw.knowledge.core.repository.KnowledgeStateRepository;
import com.meta_claw.knowledge.core.domain.WorkerResult;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
/**
 * 应用层流程编排器：接收 worker 结果并更新知识状态。
 */
public class IngestWorkerResultProcess {
    private final KnowledgeStateRepository knowledgeStateRepository;

    /** 将 worker 返回的产物和控制状态写回主状态仓库。 */
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
