package meta.claw.tool.knowledge.model;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class KnowledgeEntry {
    private String id;
    private String content;
    @Builder.Default
    private String title = "";
    @Builder.Default
    private KnowledgeType knowledgeType = KnowledgeType.UNKNOWN;
    @Builder.Default
    private List<String> topics = Collections.emptyList();
    @Builder.Default
    private KnowledgeStatus status = KnowledgeStatus.ACTIVE;
    @Builder.Default
    private Instant createdAt = Instant.now();
    @Builder.Default
    private Instant updatedAt = Instant.now();
    @Builder.Default
    private String commitHash = "";
    @Builder.Default
    private String previousVersion = "";
    @Builder.Default
    private List<String> relatedIds = Collections.emptyList();
    @Builder.Default
    private List<String> tags = Collections.emptyList();
    private Path sourceFile;
    @Builder.Default
    private int lineNumber = 0;
    @Builder.Default
    private Map<String, Object> extra = Collections.emptyMap();

    public boolean isActiveFact() {
        return knowledgeType == KnowledgeType.FACT && status == KnowledgeStatus.ACTIVE;
    }

    public String getSourceAsset() {
        return extra != null ? String.valueOf(extra.getOrDefault("source_asset", "")) : "";
    }

    public String getMediaType() {
        return extra != null ? String.valueOf(extra.getOrDefault("media_type", "text/plain")) : "text/plain";
    }

    public boolean isMultimodalUsed() {
        return extra != null && Boolean.TRUE.equals(extra.get("multimodal_used"));
    }

    public void supersede(String newId, String commitHash) {
        this.status = KnowledgeStatus.SUPERSEDED;
        this.previousVersion = newId;
        if (commitHash != null) {
            this.commitHash = commitHash;
        }
        this.updatedAt = Instant.now();
    }

    public String toMarkdown() {
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        frontmatter.put("id", id);
        frontmatter.put("title", title);
        frontmatter.put("type", knowledgeType.getValue());
        frontmatter.put("status", status.getValue());

        if (topics != null && !topics.isEmpty()) {
            frontmatter.put("topics", topics);
        }
        if (commitHash != null && !commitHash.isEmpty()) {
            frontmatter.put("commit", commitHash);
        }
        if (previousVersion != null && !previousVersion.isEmpty()) {
            frontmatter.put("superseded_by", previousVersion);
        }
        if (relatedIds != null && !relatedIds.isEmpty()) {
            frontmatter.put("related", relatedIds);
        }
        if (tags != null && !tags.isEmpty()) {
            frontmatter.put("tags", tags);
        }
        if (extra != null && !extra.isEmpty()) {
            frontmatter.putAll(extra);
        }

        StringBuilder yaml = new StringBuilder();
        for (Map.Entry<String, Object> entry : frontmatter.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (entry.getValue() instanceof List<?> list) {
                yaml.append(entry.getKey()).append(":\n");
                for (Object item : list) {
                    yaml.append("  - ").append(item).append("\n");
                }
            } else {
                yaml.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
            }
        }

        return "---\n" + yaml + "---\n\n" + (content != null ? content : "") + "\n";
    }

    public static KnowledgeEntry fromMarkdown(Path filePath, String fileContent) {
        String[] lines = fileContent.split("\n", -1);
        Map<String, Object> frontmatter = new LinkedHashMap<>();
        int bodyStart = 0;

        if (lines.length > 0 && "---".equals(lines[0].trim())) {
            int endIdx = -1;
            for (int i = 1; i < lines.length; i++) {
                if ("---".equals(lines[i].trim())) {
                    endIdx = i;
                    break;
                }
            }
            if (endIdx > 0) {
                StringBuilder yamlContent = new StringBuilder();
                for (int i = 1; i < endIdx; i++) {
                    yamlContent.append(lines[i]).append("\n");
                }
                frontmatter = parseSimpleYaml(yamlContent.toString());
                bodyStart = endIdx + 1;
            }
        }

        StringBuilder body = new StringBuilder();
        for (int i = bodyStart; i < lines.length; i++) {
            body.append(lines[i]);
            if (i < lines.length - 1) {
                body.append("\n");
            }
        }

        String typeStr = String.valueOf(frontmatter.getOrDefault("type", "unknown"));
        String statusStr = String.valueOf(frontmatter.getOrDefault("status", "active"));

        KnowledgeEntry entry = KnowledgeEntry.builder()
                .id(String.valueOf(frontmatter.getOrDefault("id", filePath.getFileName().toString().replace(".md", ""))))
                .content(body.toString().trim())
                .title(String.valueOf(frontmatter.getOrDefault("title", filePath.getFileName().toString().replace(".md", ""))))
                .knowledgeType(KnowledgeType.fromValue(typeStr))
                .status(KnowledgeStatus.fromValue(statusStr))
                .commitHash(String.valueOf(frontmatter.getOrDefault("commit", "")))
                .previousVersion(String.valueOf(frontmatter.getOrDefault("superseded_by", "")))
                .sourceFile(filePath)
                .build();

        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) frontmatter.get("topics");
        if (topics != null) {
            entry.setTopics(topics);
        }

        @SuppressWarnings("unchecked")
        List<String> related = (List<String>) frontmatter.get("related");
        if (related != null) {
            entry.setRelatedIds(related);
        }

        @SuppressWarnings("unchecked")
        List<String> tagList = (List<String>) frontmatter.get("tags");
        if (tagList != null) {
            entry.setTags(tagList);
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : frontmatter.entrySet()) {
            if (!Set.of("id", "title", "type", "topics", "status", "commit", "superseded_by", "related", "tags")
                    .contains(e.getKey())) {
                extra.put(e.getKey(), e.getValue());
            }
        }
        entry.setExtra(extra);

        return entry;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseSimpleYaml(String yamlContent) {
        Map<String, Object> result = new LinkedHashMap<>();
        String currentKey = null;
        java.util.List<String> currentList = new java.util.ArrayList<>();

        for (String line : yamlContent.split("\n")) {
            line = line.stripTrailing();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }

            if (line.strip().startsWith("- ")) {
                if (currentKey != null) {
                    String item = line.strip().substring(2).strip();
                    if ((item.startsWith("\"") && item.endsWith("\"")) || (item.startsWith("'") && item.endsWith("'"))) {
                        item = item.substring(1, item.length() - 1);
                    }
                    currentList.add(item);
                }
                continue;
            }

            if (currentKey != null && !currentList.isEmpty()) {
                result.put(currentKey, currentList);
                currentList = new java.util.ArrayList<>();
                currentKey = null;
            }

            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String key = line.substring(0, colonIdx).strip();
                String value = line.substring(colonIdx + 1).strip();
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }

                if (value.isEmpty()) {
                    currentKey = key;
                    currentList = new java.util.ArrayList<>();
                } else {
                    result.put(key, value);
                }
            }
        }

        if (currentKey != null && !currentList.isEmpty()) {
            result.put(currentKey, currentList);
        }

        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KnowledgeEntry that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}