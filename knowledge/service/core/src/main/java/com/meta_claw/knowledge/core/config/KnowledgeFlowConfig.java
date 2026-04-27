package com.meta_claw.knowledge.core.config;

import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowExecutor;
import com.meta_claw.knowledge.core.application.flow.KnowledgeFlowFacade;
import com.meta_claw.knowledge.core.application.intake.SourceIntakeConfig;
import com.meta_claw.knowledge.core.application.view.AgentViewBuilder;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSnapshotStoreRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleSourceRegistryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KnowledgeFlowConfig {

    @Bean
    public SampleKnowledgeStateRepository sampleKnowledgeStateRepository() {
        return new SampleKnowledgeStateRepository();
    }

    @Bean
    public SampleKnowledgeSpaceBindingRepository sampleKnowledgeSpaceBindingRepository() {
        return new SampleKnowledgeSpaceBindingRepository();
    }

    @Bean
    public KnowledgeFlowFacade knowledgeFlowFacade(SampleKnowledgeStateRepository stateRepo,
                                                    SampleKnowledgeSpaceBindingRepository bindingRepo) {
        KnowledgeFlowExecutor executor = new KnowledgeFlowExecutor();
        return new KnowledgeFlowFacade(
                executor.getFlowExecutor(),
                bindingRepo,
                new SampleSourceRegistryRepository(),
                new SampleSnapshotStoreRepository(),
                stateRepo,
                SourceIntakeConfig.defaultConfig()
        );
    }

    @Bean
    public AgentViewBuilder agentViewBuilder(SampleKnowledgeStateRepository stateRepo,
                                              SampleKnowledgeSpaceBindingRepository bindingRepo) {
        return new AgentViewBuilder(bindingRepo, stateRepo);
    }
}
