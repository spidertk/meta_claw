package meta.claw.core.knowledge;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.runtime.VesselContext;
import meta.claw.core.knowledge.asset.AssetManager;
import meta.claw.core.knowledge.extract.ContentExtractorService;
import meta.claw.core.knowledge.extract.ExtractionContext;
import meta.claw.core.knowledge.model.*;
import meta.claw.core.knowledge.source.AssetRef;
import meta.claw.core.knowledge.source.ExtractedDocument;
import meta.claw.core.knowledge.source.KnowledgeSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class KnowledgeManager {

    private final GitManager gitManager;
    private final KnowledgeAnalyzer analyzer;
    private final ContentExtractorService extractorService;
    private final AssetManager assetManager;

    private double confidenceThreshold = 0.9;

    @Autowired
    public KnowledgeManager(GitManager gitManager,
                            KnowledgeAnalyzer analyzer,
                            ContentExtractorService extractorService,
                            AssetManager assetManager) {
        this.gitManager = gitManager;
        this.analyzer = analyzer;
        this.extractorService = extractorService;
        this.assetManager = assetManager;
    }

    private Path getKnowledgeDir() {
        String vesselId = VesselContext.getVesselId();
        if (vesselId == null || vesselId.isBlank()) {
            vesselId = "default";
        }
        return ProjectRootFinder.getMetaClawDir().resolve("vessels").resolve(vesselId).resolve("knowledge");
    }

    private Path getVesselDir() {
        String vesselId = VesselContext.getVesselId();
        if (vesselId == null || vesselId.isBlank()) {
            vesselId = "default";
        }
        return ProjectRootFinder.getMetaClawDir().resolve("vessels").resolve(vesselId);
    }

    private void ensureKnowledgeDir(Path knowledgeDir) {
        try {
            Files.createDirectories(knowledgeDir);
        } catch (IOException e) {
            log.error("Failed to create knowledge directory: {}", e.getMessage());
        }
        gitManager.init(knowledgeDir);
    }

    public Map<String, Object> acquire(KnowledgeSource source, String context, boolean dryRun) {
        Path knowledgeDir = getKnowledgeDir();
        Path vesselDir = getVesselDir();
        ensureKnowledgeDir(knowledgeDir);

        String vesselId = VesselContext.getVesselId();
        log.info("Acquiring new knowledge for vessel {} (mediaType={})...", vesselId, source.getMediaType());

        AssetRef asset = assetManager.store(source, vesselId);
        ExtractionContext ctx = ExtractionContext.builder()
                .assetManager(assetManager)
                .vesselId(vesselId)
                .build();
        ExtractedDocument doc = extractorService.extract(source, ctx);

        List<String> keywords = analyzer.extractKeywords(doc.getMarkdownBody());
        log.debug("Extracted keywords: {}", keywords);

        List<KnowledgeEntry> relatedEntries = findRelatedEntries(keywords, knowledgeDir);
        log.debug("Found {} related entries", relatedEntries.size());

        AnalysisResult analysis = analyzer.analyze(doc, relatedEntries, context);
        log.info("Analysis complete: type={}, confidence={}", analysis.getKnowledgeType(), analysis.getConfidence());

        Map<String, Object> result = new LinkedHashMap<>();

        if (dryRun) {
            result.put("status", "analyzed");
            result.put("analysis", analysisToMap(analysis));
            result.put("related_entries", relatedEntries.stream().map(KnowledgeEntry::getId).collect(Collectors.toList()));
            result.put("dry_run", true);
            return result;
        }

        if (analysis.shouldAutoExecute(confidenceThreshold)) {
            return executeAcquire(doc, asset, analysis, relatedEntries, knowledgeDir, vesselDir);
        } else {
            result.put("status", "needs_review");
            result.put("analysis", analysisToMap(analysis));

            List<Map<String, Object>> related = relatedEntries.stream()
                    .map(e -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("id", e.getId());
                        m.put("content", e.getContent() != null ? e.getContent().substring(0, Math.min(200, e.getContent().length())) : "");
                        m.put("status", e.getStatus().getValue());
                        return m;
                    })
                    .collect(Collectors.toList());
            result.put("related_entries", related);
            result.put("message", "Confidence below threshold or contradiction detected. Manual review required.");
            return result;
        }
    }

    private List<KnowledgeEntry> findRelatedEntries(List<String> keywords, Path knowledgeDir) {
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }

        List<Path> files = gitManager.grepFiles(keywords, knowledgeDir);
        return files.stream()
                .map(this::loadEntry)
                .filter(Objects::nonNull)
                .filter(KnowledgeEntry::isActiveFact)
                .collect(Collectors.toList());
    }

    private KnowledgeEntry loadEntry(Path filePath) {
        try {
            String content = Files.readString(filePath);
            return KnowledgeEntry.fromMarkdown(filePath, content);
        } catch (IOException e) {
            log.warn("Failed to load entry from {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> executeAcquire(ExtractedDocument doc,
                                                AssetRef asset,
                                                AnalysisResult analysis,
                                                List<KnowledgeEntry> relatedEntries,
                                                Path knowledgeDir, Path vesselDir) {
        String entryId = UUID.randomUUID().toString().substring(0, 8);

        List<String> topics = analysis.getSuggestedTopics() != null && !analysis.getSuggestedTopics().isEmpty()
                ? analysis.getSuggestedTopics()
                : List.of("general");
        String topicName = topics.get(0).toLowerCase().replace(" ", "_");
        Path topicDir = knowledgeDir.resolve(topicName);
        try {
            Files.createDirectories(topicDir);
        } catch (IOException e) {
            log.error("Failed to create topic directory: {}", e.getMessage());
        }

        String title = analysis.getSuggestedTitle() != null && !analysis.getSuggestedTitle().isEmpty()
                ? analysis.getSuggestedTitle()
                : entryId;
        String filename = sanitizeFilename(title) + ".md";
        Path filePath = topicDir.resolve(filename);

        List<String> supersededIds = new ArrayList<>();
        if (analysis.getContradiction().isDetected()
                && analysis.getContradiction().getConflictingEntryId() != null
                && !analysis.getContradiction().getConflictingEntryId().isEmpty()) {
            supersededIds.add(analysis.getContradiction().getConflictingEntryId());
        }

        Map<String, Object> extra = new LinkedHashMap<>();
        if (asset != null) {
            extra.put("source_asset", "assets/" + asset.getAssetId() + "/" + asset.getOriginalPath().getFileName());
            extra.put("media_type", doc.getMediaType());
            extra.put("multimodal_used", analysis.isMultimodalUsed());
        }
        if (!supersededIds.isEmpty()) {
            extra.put("supersedes", supersededIds);
        }

        KnowledgeEntry entry = KnowledgeEntry.builder()
                .id(entryId)
                .content(doc.getMarkdownBody())
                .title(title)
                .knowledgeType(KnowledgeType.fromValue(analysis.getKnowledgeType()))
                .topics(topics)
                .status(KnowledgeStatus.ACTIVE)
                .relatedIds(relatedEntries.stream().map(KnowledgeEntry::getId).filter(id -> !id.equals(entryId)).collect(Collectors.toList()))
                .sourceFile(filePath)
                .extra(extra)
                .build();

        try {
            String markdownContent = entry.toMarkdown();
            Files.writeString(filePath, markdownContent);
        } catch (IOException e) {
            log.error("Failed to write knowledge file: {}", e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "error");
            result.put("message", "Failed to write file: " + e.getMessage());
            return result;
        }

        String commitMessage = analysis.getCommitSummary() != null && !analysis.getCommitSummary().isEmpty()
                ? analysis.getCommitSummary()
                : "Add knowledge: " + entry.getTitle();
        if (analysis.getCommitDescription() != null && !analysis.getCommitDescription().isEmpty()) {
            commitMessage += "\n\n" + analysis.getCommitDescription();
        }

        String commitHash = gitManager.commitKnowledge(filePath, commitMessage);
        entry.setCommitHash(commitHash);

        try {
            Files.writeString(filePath, entry.toMarkdown());
        } catch (IOException e) {
            log.warn("Failed to update file with commit hash: {}", e.getMessage());
        }

        log.info("Knowledge acquired: {} (commit {})", filePath, commitHash.substring(0, Math.min(8, commitHash.length())));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "committed");
        result.put("analysis", analysisToMap(analysis));
        result.put("entry_id", entryId);
        result.put("file_path", vesselDir.relativize(filePath).toString());
        result.put("commit_hash", commitHash);
        result.put("superseded_ids", supersededIds);
        if (asset != null) {
            result.put("asset_id", asset.getAssetId());
        }
        return result;
    }

    public List<Map<String, Object>> retrieve(String query, String mode, int maxResults) {
        Path knowledgeDir = getKnowledgeDir();
        Path vesselDir = getVesselDir();
        Path assetsDir = vesselDir.resolve("assets");
        ensureKnowledgeDir(knowledgeDir);

        List<String> keywords = Arrays.asList(query.toLowerCase().split("\\s+"));

        List<Path> knowledgeFiles = gitManager.grepFiles(keywords, knowledgeDir, "*.md");
        List<Path> assetFiles = Files.exists(assetsDir)
                ? gitManager.grepFiles(keywords, assetsDir, "*.md")
                : Collections.emptyList();

        Set<Path> allFiles = new LinkedHashSet<>();
        allFiles.addAll(knowledgeFiles);
        allFiles.addAll(assetFiles);

        List<Map<String, Object>> results = new ArrayList<>();
        for (Path filePath : allFiles) {
            if (results.size() >= maxResults) {
                break;
            }
            try {
                KnowledgeEntry entry = loadEntry(filePath);
                if (entry == null) {
                    continue;
                }

                Map<String, Object> result = new LinkedHashMap<>();

                if ("current".equals(mode)) {
                    if (entry.getStatus() != KnowledgeStatus.ACTIVE) {
                        continue;
                    }
                    result.put("id", entry.getId());
                    result.put("title", entry.getTitle());
                    result.put("path", vesselDir.relativize(filePath).toString());
                    result.put("content", entry.getContent());
                    result.put("snippet", entry.getContent() != null
                            ? entry.getContent().substring(0, Math.min(300, entry.getContent().length()))
                            : "");
                    result.put("type", entry.getKnowledgeType().getValue());
                    result.put("status", entry.getStatus().getValue());
                    result.put("topics", entry.getTopics());
                    result.put("media_type", entry.getMediaType());
                    result.put("source_asset", entry.getSourceAsset());
                } else if ("history".equals(mode)) {
                    GitFileHistory history = gitManager.getFileHistory(filePath, 3);
                    result.put("id", entry.getId());
                    result.put("title", entry.getTitle());
                    result.put("path", vesselDir.relativize(filePath).toString());
                    result.put("current_content", entry.getContent());
                    result.put("current_status", entry.getStatus().getValue());
                    result.put("media_type", entry.getMediaType());
                    result.put("source_asset", entry.getSourceAsset());

                    List<Map<String, Object>> historyList = history.getRecentCommits().stream()
                            .map(c -> {
                                Map<String, Object> cm = new LinkedHashMap<>();
                                cm.put("commit", c.getHash().substring(0, Math.min(8, c.getHash().length())));
                                cm.put("date", c.getDate() != null ? c.getDate().toString() : null);
                                cm.put("author", c.getAuthor());
                                cm.put("message", c.getMessage());
                                return cm;
                            })
                            .collect(Collectors.toList());
                    result.put("history", historyList);
                }

                results.add(result);
            } catch (Exception e) {
                log.warn("Failed to retrieve {}: {}", filePath, e.getMessage());
            }
        }

        return results;
    }

    public String getFull(String path) {
        Path knowledgeDir = getKnowledgeDir();
        Path vesselDir = getVesselDir();
        ensureKnowledgeDir(knowledgeDir);

        Path filePath = vesselDir.resolve(path);
        if (!Files.exists(filePath) && !path.startsWith("knowledge/")) {
            filePath = knowledgeDir.resolve(path);
        }

        if (Files.exists(filePath)) {
            try {
                return Files.readString(filePath);
            } catch (IOException e) {
                log.warn("Failed to read {}: {}", filePath, e.getMessage());
            }
        }
        return null;
    }

    public List<Map<String, Object>> listAll() {
        Path knowledgeDir = getKnowledgeDir();
        Path vesselDir = getVesselDir();
        ensureKnowledgeDir(knowledgeDir);

        if (!Files.exists(knowledgeDir)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        try (var stream = Files.walk(knowledgeDir)) {
            stream.filter(p -> p.toString().endsWith(".md"))
                    .forEach(mdFile -> {
                        try {
                            long size = Files.size(mdFile);
                            KnowledgeEntry entry = loadEntry(mdFile);
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("path", vesselDir.relativize(mdFile).toString());
                            result.put("title", entry != null ? entry.getTitle() : mdFile.getFileName().toString().replace(".md", ""));
                            result.put("id", entry != null ? entry.getId() : null);
                            result.put("type", entry != null ? entry.getKnowledgeType().getValue() : "unknown");
                            result.put("status", entry != null ? entry.getStatus().getValue() : "unknown");
                            result.put("size", size);
                            results.add(result);
                        } catch (IOException e) {
                            log.warn("Failed to list {}: {}", mdFile, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("Failed to walk knowledge directory: {}", e.getMessage());
        }

        results.sort(Comparator.comparing(r -> String.valueOf(r.get("path"))));
        return results;
    }

    public List<Map<String, Object>> getHistory(String path) {
        Path knowledgeDir = getKnowledgeDir();
        Path vesselDir = getVesselDir();
        ensureKnowledgeDir(knowledgeDir);

        Path filePath = vesselDir.resolve(path);
        if (!Files.exists(filePath) && !path.startsWith("knowledge/")) {
            filePath = knowledgeDir.resolve(path);
        }

        if (!Files.exists(filePath)) {
            return Collections.emptyList();
        }

        GitFileHistory gitHistory = gitManager.getFileHistory(filePath, 10);

        return gitHistory.getRecentCommits().stream()
                .map(c -> {
                    Map<String, Object> cm = new LinkedHashMap<>();
                    cm.put("commit", c.getHash().substring(0, Math.min(8, c.getHash().length())));
                    cm.put("date", c.getDate() != null ? c.getDate().toString() : null);
                    cm.put("author", c.getAuthor());
                    cm.put("message", c.getMessage());
                    cm.put("files_changed", c.getFilesChanged());
                    return cm;
                })
                .collect(Collectors.toList());
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "";
        return filename.strip()
                .replaceAll("[<>:\"/\\\\|?*]", "")
                .replaceAll("\\s+", "_")
                .substring(0, Math.min(100, filename.length()));
    }

    private Map<String, Object> analysisToMap(AnalysisResult analysis) {
        Map<String, Object> contradiction = new LinkedHashMap<>();
        contradiction.put("detected", analysis.getContradiction().isDetected());
        contradiction.put("conflictingEntryId", analysis.getContradiction().getConflictingEntryId());
        contradiction.put("explanation", analysis.getContradiction().getExplanation());
        contradiction.put("confidence", analysis.getContradiction().getConfidence());
        contradiction.put("contradictionType", analysis.getContradiction().getContradictionType());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("knowledgeType", analysis.getKnowledgeType());
        map.put("isFact", analysis.isFact());
        map.put("contradiction", contradiction);
        map.put("confidence", analysis.getConfidence());
        map.put("recommendedAction", analysis.getRecommendedAction());
        map.put("reasoning", analysis.getReasoning());
        map.put("extractedKeywords", analysis.getExtractedKeywords());
        map.put("suggestedTopics", analysis.getSuggestedTopics());
        map.put("suggestedTitle", analysis.getSuggestedTitle());
        map.put("commitSummary", analysis.getCommitSummary());
        map.put("commitDescription", analysis.getCommitDescription());
        return map;
    }
}