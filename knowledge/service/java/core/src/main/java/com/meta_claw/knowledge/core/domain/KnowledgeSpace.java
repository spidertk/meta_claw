package com.meta_claw.knowledge.core.domain;

public record KnowledgeSpace(
        String spaceId,
        String path,
        boolean shared,
        String description
) {
}
