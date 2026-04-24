package com.meta_claw.knowledge.core.repository;

import com.meta_claw.knowledge.core.domain.AgentRoleBinding;
import com.meta_claw.knowledge.core.domain.KnowledgeSpace;

import java.util.Optional;

public interface KnowledgeRegistryRepository {
    Optional<AgentRoleBinding> findRoleBinding(String roleName);

    Optional<KnowledgeSpace> findSpace(String spaceId);
}
