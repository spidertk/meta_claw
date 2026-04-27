package com.meta_claw.knowledge.core.flow.register;

import com.meta_claw.knowledge.core.TestFixtures;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowExecutor;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.domain.SourceRegistrationResult;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSnapshotStoreRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSourceRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterSourceChainTest {

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
    @DisplayName("首次注册来源生成新 snapshot")
    void firstRegistrationCreatesSnapshot() {
        RegisterSourceFlowContext context = RegisterSourceFlowContext.builder()
                .request(TestFixtures.sampleRepoRequest())
                .build();

        SourceRegistrationResult result = facade.registerSource(context);

        assertThat(result.getSourceRecord()).isNotNull();
        assertThat(result.getSourceRecord().getSourceId()).isNotBlank();
        assertThat(result.getSnapshotRecord()).isNotNull();
        assertThat(result.getSnapshotRecord().getSnapshotId()).isNotBlank();
        assertThat(result.isUnchanged()).isFalse();
    }

    @Test
    @DisplayName("重复注册相同来源返回 unchanged")
    void repeatedRegistrationIsUnchanged() {
        RegisterSourceFlowContext context = RegisterSourceFlowContext.builder()
                .request(TestFixtures.sampleRepoRequest())
                .build();

        facade.registerSource(context);
        SourceRegistrationResult second = facade.registerSource(context);

        assertThat(second.isUnchanged()).isTrue();
        assertThat(second.getSourceRecord().getLatestSnapshotId())
                .isEqualTo(second.getSnapshotRecord().getSnapshotId());
    }
}
