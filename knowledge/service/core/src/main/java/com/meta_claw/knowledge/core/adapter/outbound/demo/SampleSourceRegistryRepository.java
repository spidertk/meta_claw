package com.meta_claw.knowledge.core.adapter.inbound.demo;

import lombok.extern.slf4j.Slf4j;

import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.meta_claw.knowledge.core.repository.SourceRegistryRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
/**
 * 演示用来源注册表实现。
 * 仅用于本地内存示例，不代表最终持久化模型。
 */
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
