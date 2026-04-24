package com.meta_claw.knowledge.core.domain;

import java.util.Optional;

public interface SourceRegistryRepository {
    void save(SourceRecord sourceRecord);

    Optional<SourceRecord> findById(String sourceId);
}
