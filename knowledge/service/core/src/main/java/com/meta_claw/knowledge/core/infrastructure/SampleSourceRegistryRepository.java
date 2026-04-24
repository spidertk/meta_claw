package com.meta_claw.knowledge.core.infrastructure;

import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.meta_claw.knowledge.core.domain.SourceRegistryRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SampleSourceRegistryRepository implements SourceRegistryRepository {
    private final Map<String, SourceRecord> sources = new ConcurrentHashMap<>();

    @Override
    public void save(SourceRecord sourceRecord) {
        sources.put(sourceRecord.getSourceId(), sourceRecord);
        log.debug("Saved source {}", sourceRecord.getSourceId());
    }

    @Override
    public Optional<SourceRecord> findById(String sourceId) {
        return Optional.ofNullable(sources.get(sourceId));
    }
}
