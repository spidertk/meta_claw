package com.meta_claw.knowledge.core.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.WorkerJob;

@Slf4j
@RequiredArgsConstructor
public class SubmitWorkerJobUseCase {
    public WorkerJob execute(WorkerJob workerJob) {
        log.debug("Submitting worker job {} for space {}", workerJob.getJobId(), workerJob.getSpaceId());
        return workerJob;
    }
}
