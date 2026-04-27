package com.meta_claw.knowledge.core.flow.resume;

import com.meta_claw.knowledge.core.TestFixtures;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowExecutor;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.flow.context.ResumeSnapshotScanFlowContext;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSnapshotStoreRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSourceRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeSnapshotScanChainTest {

    private KnowledgeFlowFacade facade;

    @BeforeEach
    void setUp() {
        KnowledgeFlowExecutor executor = new KnowledgeFlowExecutor();
        facade = new KnowledgeFlowFacade(
                executor.getFlowExecutor(),
                new SampleKnowledgeSpaceBindingRepository(),
                new SampleSourceRegistryRepository(),
                new SampleSnapshotStoreRepository(),
                new SampleKnowledgeStateRepository(),
                SourceIntakeConfig.defaultConfig()
        );
    }

    @Test
    @DisplayName("resume 来源生成新 snapshot batch")
    void resumeScanGeneratesNextBatch() {
        var reg = facade.registerSource(RegisterSourceFlowContext.builder()
                .request(TestFixtures.sampleRepoRequest())
                .build());

        String sourceId = reg.getSourceRecord().getSourceId();
        SnapshotRecord resumed = facade.resumeSnapshotScan(sourceId);

        assertThat(resumed).isNotNull();
        assertThat(resumed.getSourceId()).isEqualTo(sourceId);
    }
}
