package com.meta_claw.knowledge.core.domain;

import java.util.List;

public record AgentRoleBinding(
        String roleName,
        String spaceId,
        String spacePath,
        List<String> inherits,
        String templateId
) {
}
