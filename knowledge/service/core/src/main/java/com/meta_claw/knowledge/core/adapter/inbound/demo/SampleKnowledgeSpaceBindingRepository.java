package com.meta_claw.knowledge.core.adapter.inbound.demo;

import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.repository.KnowledgeSpaceBindingRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
/**
 * 演示用知识空间绑定仓库实现。
 * 负责根据角色名返回知识空间绑定关系，用于本地 sample 装配。
 */
public class SampleKnowledgeSpaceBindingRepository implements KnowledgeSpaceBindingRepository {
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
    public Optional<AgentRoleBinding> findByRoleName(String roleName) {
        log.debug("Looking up role binding for {}", roleName);
        return Optional.ofNullable(bindings.get(roleName));
    }
}
