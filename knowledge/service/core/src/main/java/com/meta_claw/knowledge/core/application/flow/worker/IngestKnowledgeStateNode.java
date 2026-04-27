package com.meta_claw.knowledge.core.application.flow.worker;

import com.meta_claw.knowledge.core.application.flow.context.IngestWorkerResultFlowContext;
import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import com.meta_claw.knowledge.core.domain.KnowledgeControlState;
import com.meta_claw.knowledge.core.domain.WorkerResult;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

@Slf4j
public class IngestKnowledgeStateNode extends NodeComponent {

    @Override
    public void process() {
        IngestWorkerResultFlowContext context = this.getContextBean(IngestWorkerResultFlowContext.class);
        WorkerResult workerResult = context.getWorkerResult();
        for (KnowledgeAsset asset : workerResult.getArtifacts()) {
            context.getRuntimeDependencies().getKnowledgeStateRepository().saveAsset(asset);
            context.getRuntimeDependencies().getKnowledgeStateRepository().saveControlState(KnowledgeControlState.builder()
                    .spaceId(workerResult.getSpaceId())
                    .assetId(asset.getAssetId())
                    .reviewStatus(asset.getStatus())
                    .issues(workerResult.getIssues())
                    .updatedAt(Instant.now())
                    .build());
        }
        log.info("Ingested worker result {} for space {}", workerResult.getJobId(), workerResult.getSpaceId());
    }
}
