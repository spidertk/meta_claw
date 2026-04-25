package com.meta_claw.knowledge.core.repository;

import com.meta_claw.knowledge.core.domain.AgentRoleBinding;

import java.util.Optional;

/**
 * 角色到知识空间绑定关系的读取接口。
 * 负责把外部角色名解析为知识空间绑定配置。
 */
public interface KnowledgeSpaceBindingRepository {
    /** 按角色名读取知识空间绑定关系。 */
    Optional<AgentRoleBinding> findByRoleName(String roleName);
}
