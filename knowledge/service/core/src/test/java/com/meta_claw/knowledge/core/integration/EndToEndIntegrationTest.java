package com.meta_claw.knowledge.core.integration;

import com.meta_claw.knowledge.core.TestFixtures;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.SubmitWorkerJobFlowContext;
import com.meta_claw.knowledge.core.application.view.AgentViewBuilder;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import com.meta_claw.knowledge.core.domain.WorkerJob;
import com.meta_claw.knowledge.core.api.req.SubmitWorkerJobRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EndToEndIntegrationTest {

    @Autowired
    private KnowledgeFlowFacade flowFacade;

    @Autowired
    private AgentViewBuilder viewBuilder;

    @Test
    @DisplayName("来源注册 → snapshot → job → view 全链路")
    void fullPipeline() {
        // 1. 注册来源
        SourceRegistrationResult reg = flowFacade.registerSource(
                RegisterSourceFlowContext.builder()
                        .request(TestFixtures.sampleRepoRequest())
                        .build()
        );
        assertThat(reg.getSourceRecord().getSourceId()).isNotBlank();

        // 2. 提交 job
        WorkerJob job = flowFacade.submitWorkerJob(
                SubmitWorkerJobFlowContext.builder()
                        .request(SubmitWorkerJobRequest.builder()
                                .roleName("finance_advisor")
                                .jobId("job_e2e_001")
                                .jobType("extract_graph_and_wiki")
                                .sourceId(reg.getSourceRecord().getSourceId())
                                .snapshotId(reg.getSnapshotRecord().getSnapshotId())
                                .expectedArtifacts(java.util.List.of("graph", "wiki"))
                                .build())
                        .build()
        );
        assertThat(job.getJobId()).isEqualTo("job_e2e_001");

        // 3. 查询 view
        String view = viewBuilder.buildMarkdownView("finance_advisor");
        assertThat(view).contains("Knowledge View for finance_advisor");
    }
}
