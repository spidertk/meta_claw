package meta.claw.core.knowledge.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.infra.ProjectRootFinder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 资产注册表：按内容 sha256 登记已入库资产及其提炼出的知识条目，
 * 用于同内容资产的幂等去重与「已录入」快速路径。
 * <p>持久化在 .meta-claw/vessels/{vesselId}/assets/index.json。</p>
 */
@Slf4j
@Component
public class AssetRegistry {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Map<String, AssetRecord>> cache = new LinkedHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssetRecord {
        private String sha256;
        private String assetId;
        private String mediaType;
        @Builder.Default
        private List<String> knowledgeEntryIds = new ArrayList<>();
        private String createdAt;
    }

    public synchronized Optional<AssetRecord> findByHash(String vesselId, String sha256) {
        if (sha256 == null || sha256.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(load(vesselId).get(sha256));
    }

    public synchronized AssetRecord register(String vesselId, String sha256, String assetId, String mediaType) {
        Map<String, AssetRecord> index = load(vesselId);
        AssetRecord record = AssetRecord.builder()
                .sha256(sha256)
                .assetId(assetId)
                .mediaType(mediaType)
                .knowledgeEntryIds(new ArrayList<>())
                .createdAt(Instant.now().toString())
                .build();
        index.put(sha256, record);
        persist(vesselId, index);
        return record;
    }

    public synchronized void linkKnowledge(String vesselId, String sha256, String entryId) {
        if (sha256 == null || sha256.isBlank() || entryId == null || entryId.isBlank()) {
            return;
        }
        Map<String, AssetRecord> index = load(vesselId);
        AssetRecord record = index.get(sha256);
        if (record == null) {
            log.warn("Cannot link knowledge {} : no asset record for hash {}", entryId, sha256);
            return;
        }
        if (!record.getKnowledgeEntryIds().contains(entryId)) {
            record.getKnowledgeEntryIds().add(entryId);
            persist(vesselId, index);
        }
    }

    private Map<String, AssetRecord> load(String vesselId) {
        String key = vesselId != null && !vesselId.isBlank() ? vesselId : "default";
        Map<String, AssetRecord> cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        Map<String, AssetRecord> index = new LinkedHashMap<>();
        Path indexFile = indexFile(key);
        if (Files.exists(indexFile)) {
            try {
                Map<String, Object> raw = objectMapper.readValue(indexFile.toFile(), Map.class);
                for (Map.Entry<String, Object> e : raw.entrySet()) {
                    Map<String, Object> r = (Map<String, Object>) e.getValue();
                    List<String> entryIds = new ArrayList<>();
                    Object ids = r.get("knowledgeEntryIds");
                    if (ids instanceof List<?> list) {
                        for (Object id : list) {
                            entryIds.add(String.valueOf(id));
                        }
                    }
                    index.put(e.getKey(), AssetRecord.builder()
                            .sha256(String.valueOf(r.getOrDefault("sha256", e.getKey())))
                            .assetId(String.valueOf(r.getOrDefault("assetId", "")))
                            .mediaType(String.valueOf(r.getOrDefault("mediaType", "")))
                            .knowledgeEntryIds(entryIds)
                            .createdAt(String.valueOf(r.getOrDefault("createdAt", "")))
                            .build());
                }
            } catch (IOException e) {
                log.warn("Failed to load asset index {}: {}", indexFile, e.getMessage());
            }
        }
        cache.put(key, index);
        return index;
    }

    private void persist(String vesselId, Map<String, AssetRecord> index) {
        Path indexFile = indexFile(vesselId);
        try {
            Files.createDirectories(indexFile.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexFile.toFile(), index);
        } catch (IOException e) {
            log.warn("Failed to persist asset index {}: {}", indexFile, e.getMessage());
        }
    }

    private Path indexFile(String vesselId) {
        return ProjectRootFinder.getMetaClawDir()
                .resolve("vessels")
                .resolve(vesselId != null && !vesselId.isBlank() ? vesselId : "default")
                .resolve("assets")
                .resolve("index.json");
    }
}
