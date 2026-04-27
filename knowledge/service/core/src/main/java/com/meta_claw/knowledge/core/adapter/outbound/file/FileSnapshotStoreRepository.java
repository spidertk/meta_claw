package com.meta_claw.knowledge.core.adapter.outbound.file;

import com.meta_claw.knowledge.core.domain.SnapshotRecord;
import com.meta_claw.knowledge.core.repository.SnapshotStoreRepository;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
/**
 * 基于 JSON Lines 文件的快照历史存储。
 * 每行保存一个 SnapshotRecord，用于在没有数据库前保留本地扫描状态。
 */
public class FileSnapshotStoreRepository implements SnapshotStoreRepository {
    private final Path filePath;
    private final Map<String, SnapshotRecord> snapshots = new LinkedHashMap<>();

    public FileSnapshotStoreRepository(Path filePath) {
        this.filePath = filePath;
        load();
    }

    @Override
    public void save(SnapshotRecord snapshotRecord) {
        snapshots.put(snapshotRecord.getSnapshotId(), snapshotRecord);
        flush();
        log.debug("Saved snapshot {} to {}", snapshotRecord.getSnapshotId(), filePath);
    }

    @Override
    public Optional<SnapshotRecord> findById(String snapshotId) {
        return Optional.ofNullable(snapshots.get(snapshotId));
    }

    @Override
    public Optional<SnapshotRecord> findLatestBySourceId(String sourceId) {
        return snapshots.values().stream()
                .filter(snapshot -> snapshot.getSourceId().equals(sourceId))
                .max(Comparator.comparing(SnapshotRecord::getCapturedAt));
    }

    /** 从 JSONL 文件加载已有快照历史。 */
    private void load() {
        if (!Files.exists(filePath)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
                if (line.isBlank()) {
                    continue;
                }
                SnapshotRecord snapshotRecord = JacksonJsonLineSupport.readLine(line, SnapshotRecord.class);
                snapshots.put(snapshotRecord.getSnapshotId(), snapshotRecord);
            }
            log.info("Loaded {} snapshots from {}", snapshots.size(), filePath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load snapshot store: " + filePath, e);
        }
    }

    /** 将快照索引整体重写到 JSONL 文件，保持实现简单且易检查。 */
    private void flush() {
        try {
            Files.createDirectories(filePath.getParent());
            List<String> lines = snapshots.values().stream()
                    .map(JacksonJsonLineSupport::writeLine)
                    .toList();
            Files.write(filePath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write snapshot store: " + filePath, e);
        }
    }
}
