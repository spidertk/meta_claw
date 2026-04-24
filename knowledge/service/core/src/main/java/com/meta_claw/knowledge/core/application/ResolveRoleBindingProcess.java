package com.meta_claw.knowledge.core.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.repository.KnowledgeRegistryRepository;

@Slf4j
@RequiredArgsConstructor
/**
 * 应用层流程编排器：按角色解析知识空间绑定关系。
 */
public class ResolveRoleBindingProcess {
    private final KnowledgeRegistryRepository knowledgeRegistryRepository;

    /** 执行一次角色绑定解析流程。 */
    public AgentRoleBinding execute(String roleName) {
        AgentRoleBinding binding = knowledgeRegistryRepository.findRoleBinding(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleName));
        log.debug("Resolved role {} to space {}", roleName, binding.getSpaceId());
        return binding;
    }
}
