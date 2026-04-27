package com.meta_claw.knowledge.core.application.flow.context;

import com.meta_claw.knowledge.core.domain.WorkerResult;
import com.meta_claw.knowledge.core.transport.worker.WorkerResultEnvelope;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class IngestWorkerResultFlowContext {

    private WorkerResultEnvelope envelope;
    private WorkerResult workerResult;
    private WorkerResult result;
    private FlowRuntimeDependencies runtimeDependencies;
}
