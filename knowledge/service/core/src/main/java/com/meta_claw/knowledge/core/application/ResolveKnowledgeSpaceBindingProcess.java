package com.meta_claw.knowledge.core.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.repository.KnowledgeSpaceBindingRepository;

@Slf4j
@RequiredArgsConstructor
/**
 * 应用层流程编排器：按角色解析知识空间绑定关系。
 */
public class ResolveKnowledgeSpaceBindingProcess {
    private final KnowledgeSpaceBindingRepository knowledgeSpaceBindingRepository;

    /** 执行一次角色绑定解析流程。 */
    public AgentRoleBinding execute(String roleName) {
        AgentRoleBinding binding = knowledgeSpaceBindingRepository.findByRoleName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + roleName));
        log.debug("Resolved role {} to space {}", roleName, binding.getSpaceId());
        return binding;
    }
}
