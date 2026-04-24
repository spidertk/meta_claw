package com.meta_claw.knowledge.core.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.domain.KnowledgeRegistryRepository;

@Slf4j
@RequiredArgsConstructor
public class ResolveKnowledgeSpaceUseCase {
    private final KnowledgeRegistryRepository knowledgeRegistryRepository;

    public AgentRoleBinding execute(String roleName) {
        AgentRoleBinding binding = knowledgeRegistryRepository.findRoleBinding(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleName));
        log.debug("Resolved role {} to space {}", roleName, binding.getSpaceId());
        return binding;
    }
}
