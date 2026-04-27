package com.meta_claw.knowledge.core.application.flow.worker;

import com.meta_claw.knowledge.core.application.flow.context.IngestWorkerResultFlowContext;
import com.meta_claw.knowledge.core.application.state.KnowledgeStatePersister;
import com.meta_claw.knowledge.core.domain.WorkerResult;
import com.yomahub.liteflow.core.NodeComponent;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IngestKnowledgeStateNode extends NodeComponent {

    @Override
    public void process() {
        IngestWorkerResultFlowContext context = this.getContextBean(IngestWorkerResultFlowContext.class);
        WorkerResult workerResult = context.getWorkerResult();
        if (workerResult == null || workerResult.getArtifacts() == null) {
            log.warn("No worker result or artifacts to ingest");
            return;
        }

        KnowledgeStatePersister persister = new KnowledgeStatePersister(
                context.getRuntimeDependencies().getKnowledgeStateRepository()
        );
        persister.persistAll(workerResult.getSpaceId(), workerResult.getJobId(), workerResult.getArtifacts());

        log.info("Ingested worker result {} for space {}", workerResult.getJobId(), workerResult.getSpaceId());
    }
}
