package com.meta_claw.knowledge.core.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.WorkerJob;

@Slf4j
@RequiredArgsConstructor
/**
 * 应用层流程编排器：提交待执行的 worker job。
 */
public class SubmitWorkerJobProcess {
    /** 当前骨架阶段只返回已构造的 job，后续可扩展真实提交流程。 */
    public WorkerJob execute(WorkerJob workerJob) {
        log.debug("Submitting worker job {} for space {}", workerJob.getJobId(), workerJob.getSpaceId());
        return workerJob;
    }
}
