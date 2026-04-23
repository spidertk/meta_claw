package com.meta_claw.knowledge.core.infrastructure;

import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.domain.KnowledgeRegistryRepository;
import com.meta_claw.knowledge.core.domain.KnowledgeSpace;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class SampleKnowledgeRegistryRepository implements KnowledgeRegistryRepository {
    private final Map<String, KnowledgeSpace> spaces = Map.of(
            "ks_shared_common", new KnowledgeSpace(
                    "ks_shared_common",
                    "knowledge/shared/ks_shared_common",
                    true,
                    "Common reusable knowledge."
            ),
            "ks_agent_finance", new KnowledgeSpace(
                    "ks_agent_finance",
                    "knowledge/spaces/ks_agent_finance",
                    false,
                    "Finance advisor private knowledge space."
            ),
            "ks_agent_homebuying", new KnowledgeSpace(
                    "ks_agent_homebuying",
                    "knowledge/spaces/ks_agent_homebuying",
                    false,
                    "Homebuying advisor private knowledge space."
            )
    );

    private final Map<String, AgentRoleBinding> bindings = Map.of(
            "finance_advisor", new AgentRoleBinding(
                    "finance_advisor",
                    "ks_agent_finance",
                    "knowledge/spaces/ks_agent_finance",
                    List.of("shared_common"),
                    "advisor_template_v1"
            ),
            "homebuying_advisor", new AgentRoleBinding(
                    "homebuying_advisor",
                    "ks_agent_homebuying",
                    "knowledge/spaces/ks_agent_homebuying",
                    List.of("shared_common"),
                    "advisor_template_v1"
            )
    );

    @Override
    public Optional<AgentRoleBinding> findRoleBinding(String roleName) {
        return Optional.ofNullable(bindings.get(roleName));
    }

    @Override
    public Optional<KnowledgeSpace> findSpace(String spaceId) {
        return Optional.ofNullable(spaces.get(spaceId));
    }
}
