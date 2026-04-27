package com.meta_claw.knowledge.core.flow.register;

import com.meta_claw.knowledge.core.TestFixtures;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowExecutor;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.flow.context.RegisterSourceFlowContext;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSnapshotStoreRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSourceRegistryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailurePathTest {

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
    @DisplayName("不存在的 role 抛出异常，不写入 registry")
    void unknownRoleDoesNotCorruptRegistry() {
        var request = TestFixtures.sampleRepoRequest().toBuilder()
                .roleName("non_existent_role")
                .build();

        assertThatThrownBy(() -> facade.registerSource(
                RegisterSourceFlowContext.builder().request(request).build()
        )).isInstanceOf(Exception.class);
    }
}
