package com.meta_claw.knowledge.core.domain;

import java.util.Optional;

public interface KnowledgeRegistryRepository {
    Optional<AgentRoleBinding> findRoleBinding(String roleName);

    Optional<KnowledgeSpace> findSpace(String spaceId);
}
