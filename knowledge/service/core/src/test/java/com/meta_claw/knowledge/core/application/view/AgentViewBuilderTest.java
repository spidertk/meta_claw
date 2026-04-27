package com.meta_claw.knowledge.core.application.view;

import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeSpaceBindingRepository;
import com.meta_claw.knowledge.core.adapter.outbound.demo.SampleKnowledgeStateRepository;
import com.meta_claw.knowledge.core.domain.KnowledgeAsset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentViewBuilderTest {

    @Test
    @DisplayName("按 role 过滤可见 space 并生成 markdown view")
    void buildViewForRole() {
        SampleKnowledgeStateRepository stateRepo = new SampleKnowledgeStateRepository();
        stateRepo.saveAsset(KnowledgeAsset.builder()
                .spaceId("ks_agent_finance")
                .assetId("asset_1")
                .assetType("graph")
                .status("ready")
                .coverage("partial")
                .build());

        AgentViewBuilder builder = new AgentViewBuilder(
                new SampleKnowledgeSpaceBindingRepository(),
                stateRepo
        );

        String view = builder.buildMarkdownView("finance_advisor");

        assertThat(view).contains("Knowledge View for finance_advisor");
        assertThat(view).contains("graph");
        assertThat(view).contains("asset_1");
    }
}
