package com.meta_claw.knowledge.core.adapter.inbound.demo;

import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.repository.KnowledgeRegistryRepository;
import com.meta_claw.knowledge.core.domain.KnowledgeSpace;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class SampleKnowledgeRegistryRepository implements KnowledgeRegistryRepository {
    private final Map<String, KnowledgeSpace> spaces = Map.of(
            "ks_shared_common", KnowledgeSpace.builder()
                    .spaceId("ks_shared_common")
                    .path("/meta_claw/knowledge_shared/ks_shared_common")
                    .shared(true)
                    .description("Common reusable knowledge.")
                    .build(),
            "ks_agent_finance", KnowledgeSpace.builder()
                    .spaceId("ks_agent_finance")
                    .path("/external/agents/finance_advisor/knowledge_spaces/ks_agent_finance")
                    .shared(false)
                    .description("Finance advisor private knowledge space.")
                    .build(),
            "ks_agent_homebuying", KnowledgeSpace.builder()
                    .spaceId("ks_agent_homebuying")
                    .path("/external/agents/homebuying_advisor/knowledge_spaces/ks_agent_homebuying")
                    .shared(false)
                    .description("Homebuying advisor private knowledge space.")
                    .build()
    );

    private final Map<String, AgentRoleBinding> bindings = Map.of(
            "finance_advisor", AgentRoleBinding.builder()
                    .roleName("finance_advisor")
                    .spaceId("ks_agent_finance")
                    .spacePath("/external/agents/finance_advisor/knowledge_spaces/ks_agent_finance")
                    .inherits(List.of("shared_common"))
                    .templateId("advisor_template_v1")
                    .build(),
            "homebuying_advisor", AgentRoleBinding.builder()
                    .roleName("homebuying_advisor")
                    .spaceId("ks_agent_homebuying")
                    .spacePath("/external/agents/homebuying_advisor/knowledge_spaces/ks_agent_homebuying")
                    .inherits(List.of("shared_common"))
                    .templateId("advisor_template_v1")
                    .build()
    );

    @Override
    public Optional<AgentRoleBinding> findRoleBinding(String roleName) {
        log.debug("Looking up role binding for {}", roleName);
        return Optional.ofNullable(bindings.get(roleName));
    }

    @Override
    public Optional<KnowledgeSpace> findSpace(String spaceId) {
        return Optional.ofNullable(spaces.get(spaceId));
    }
}
