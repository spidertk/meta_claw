package com.meta_claw.knowledge.core.application.flow.support;

import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.repository.KnowledgeSpaceBindingRepository;

/**
 * 共享的 role -> knowledge space 解析器。
 * 只负责把 roleName 解析成稳定的 knowledge space binding，不感知具体 flow context。
 */
public class KnowledgeSpaceResolver {

    private final KnowledgeSpaceBindingRepository knowledgeSpaceBindingRepository;

    public KnowledgeSpaceResolver(KnowledgeSpaceBindingRepository knowledgeSpaceBindingRepository) {
        this.knowledgeSpaceBindingRepository = knowledgeSpaceBindingRepository;
    }

    public AgentRoleBinding resolve(String roleName) {
        return knowledgeSpaceBindingRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleName));
    }
}
