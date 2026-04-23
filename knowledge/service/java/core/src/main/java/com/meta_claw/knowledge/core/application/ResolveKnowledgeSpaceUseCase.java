package com.meta_claw.knowledge.core.application;

import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.domain.KnowledgeRegistryRepository;

public class ResolveKnowledgeSpaceUseCase {
    private final KnowledgeRegistryRepository knowledgeRegistryRepository;

    public ResolveKnowledgeSpaceUseCase(KnowledgeRegistryRepository knowledgeRegistryRepository) {
        this.knowledgeRegistryRepository = knowledgeRegistryRepository;
    }

    public AgentRoleBinding execute(String roleName) {
        return knowledgeRegistryRepository.findRoleBinding(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleName));
    }
}
