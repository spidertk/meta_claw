package com.meta_claw.knowledge.core.adapter.outbound.file;

import com.meta_claw.knowledge.core.domain.SourceRecord;
import com.meta_claw.knowledge.core.repository.SourceRegistryRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
/**
 * 基于 JSON Lines 文件的来源注册表实现。
 * 每行保存一个 SourceRecord 当前视图，适合作为 Sprint 2 本地持久化过渡方案。
 */
public class FileSourceRegistryRepository implements SourceRegistryRepository {
    private final Path filePath;
    private final Map<String, SourceRecord> sources = new LinkedHashMap<>();

    public FileSourceRegistryRepository(Path filePath) {
        this.filePath = filePath;
        load();
    }

    @Override
    public void save(SourceRecord sourceRecord) {
        sources.put(sourceRecord.getSourceId(), sourceRecord);
        flush();
        log.debug("Saved source {} to {}", sourceRecord.getSourceId(), filePath);
    }

    @Override
    public Optional<SourceRecord> findById(String sourceId) {
        return Optional.ofNullable(sources.get(sourceId));
    }

    /** 从 JSONL 文件加载已有来源当前视图。 */
    private void load() {
        if (!Files.exists(filePath)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                SourceRecord sourceRecord = JacksonJsonLineSupport.readLine(line, SourceRecord.class);
                sources.put(sourceRecord.getSourceId(), sourceRecord);
            }
            log.info("Loaded {} sources from {}", sources.size(), filePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load source registry: " + filePath, e);
        }
    }

    /** 将内存索引整体重写到 JSONL 文件，保证每个 sourceId 只有一个当前视图。 */
    private void flush() {
        try {
            Files.createDirectories(filePath.getParent());
            List<String> lines = sources.values().stream()
                    .map(JacksonJsonLineSupport::writeLine)
                    .toList();
            Files.write(filePath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write source registry: " + filePath, e);
        }
    }
}
