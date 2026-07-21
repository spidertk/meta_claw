package meta.claw.core.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.knowledge.asset.AssetManager;
import meta.claw.core.knowledge.asset.AssetRegistry;
import meta.claw.core.knowledge.review.KnowledgeReviewGate;
import meta.claw.core.knowledge.review.ReviewDecision;
import meta.claw.core.runtime.VesselContext;
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
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class KnowledgeManager {

    private final GitManager gitManager;
    private final KnowledgeAnalyzer analyzer;
    private final ContentExtractorService extractorService;
    private final AssetManager assetManager;

    /** 资产注册表：内容 hash 去重与「已录入」快速路径。 */
    @Autowired(required = false)
    private AssetRegistry assetRegistry;

    /** 知识提案人审网关（HITL）：落库前必经确认。为空时一律挂起。 */
    @Autowired(required = false)
    private KnowledgeReviewGate reviewGate;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    /** 测试/装配用：注入资产注册表。 */
    public void setAssetRegistry(AssetRegistry assetRegistry) {
        this.assetRegistry = assetRegistry;
    }

    /** 测试/装配用：注入人审网关。 */
    public void setReviewGate(KnowledgeReviewGate reviewGate) {
        this.reviewGate = reviewGate;
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
        return acquire(source, context, dryRun, false);
    }

    /**
     * 知识采集主流程：
     * <ol>
     *   <li>资产按内容 hash 幂等入库；已录入且已提炼过的资产直接返回已有知识（零 LLM 调用）</li>
     *   <li>内容提取（图片/PDF 视觉理解与关键词合并为一次调用）</li>
     *   <li>程序化检索相关条目（git grep，无 LLM）</li>
     *   <li>一次统一知识分析调用（含矛盾自检）</li>
     *   <li>非 dryRun 时持久化待审提案并走人审网关（HITL），确认后才落库</li>
     * </ol>
     * dryRun=true 时只做第 1、2 步（如图片识别），跳过关键词/矛盾自检等分析 LLM 调用；
     * 提取结果仍持久化为待审提案，approve 时才补跑分析（见 {@link #commitProposal}），无需重新提取。
     *
     * @param force 为 true 时跳过「已录入」快速路径，强制重新提炼
     */
    public Map<String, Object> acquire(KnowledgeSource source, String context, boolean dryRun, boolean force) {
        Path knowledgeDir = getKnowledgeDir();
        Path vesselDir = getVesselDir();
        ensureKnowledgeDir(knowledgeDir);

        String vesselId = VesselContext.getVesselId();
        log.info("Acquiring new knowledge for vessel {} (mediaType={})...", vesselId, source.getMediaType());

        AssetRef asset = assetManager.store(source, vesselId);

        // 快速路径：同内容资产已处理过 → 直接返回，零 LLM 调用
        if (!force && asset.isAlreadyExists()) {
            if (assetRegistry != null) {
                Optional<AssetRegistry.AssetRecord> record = assetRegistry.findByHash(
                        vesselId != null && !vesselId.isBlank() ? vesselId : "default", asset.getSha256());
                if (record.isPresent() && !record.get().getKnowledgeEntryIds().isEmpty()) {
                    log.info("Asset {} already known ({} entries), skipping re-analysis",
                            asset.getAssetId(), record.get().getKnowledgeEntryIds().size());
                    return alreadyKnownResult(record.get(), asset, knowledgeDir, vesselDir);
                }
            }
            // 已有同资产待审提案 → 直接复用，不重复消耗 LLM
            String pendingId = findPendingProposalByHash(knowledgeDir, asset.getSha256());
            if (pendingId != null) {
                log.info("Asset {} already has pending proposal {}, reusing it", asset.getAssetId(), pendingId);
                return existingProposalResult(knowledgeDir, pendingId, dryRun);
            }
        }

        ExtractionContext ctx = ExtractionContext.builder()
                .assetManager(assetManager)
                .vesselId(vesselId)
                .sourceAsset(asset)
                .build();
        ExtractedDocument doc = extractorService.extract(source, ctx);

        Map<String, Object> result = new LinkedHashMap<>();

        // dryRun：只做内容提取（如图片识别），跳过关键词/检索/矛盾自检等分析 LLM 调用。
        // 提案不带 analysis 持久化，approve 时在 commitProposal 里补跑分析，无需重新提取。
        if (dryRun) {
            KnowledgeProposal proposal = KnowledgeProposal.builder()
                    .proposalId(UUID.randomUUID().toString().substring(0, 8))
                    .vesselId(vesselId != null && !vesselId.isBlank() ? vesselId : "default")
                    .assetId(asset != null ? asset.getAssetId() : null)
                    .sha256(asset != null ? asset.getSha256() : null)
                    .mediaType(doc.getMediaType())
                    .markdownBody(doc.getMarkdownBody())
                    .context(context)
                    .createdAt(Instant.now().toString())
                    .build();
            persistProposal(knowledgeDir, proposal);
            result.put("status", "extracted");
            result.put("proposal_id", proposal.getProposalId());
            result.put("preview", renderProposalPreview(proposal, Collections.emptyList()));
            result.put("content", doc.getMarkdownBody());
            result.put("dry_run", true);
            result.put("message", "Dry run: extraction only, analysis/contradiction check skipped. " +
                    "To save this knowledge, approve proposal '" + proposal.getProposalId() +
                    "' via knowledgeReview (analysis runs once at approval, no re-extraction needed).");
            return result;
        }

        // 提取阶段已产出关键词（视觉理解合并且）时不再单独调用 LLM
        List<String> keywords = doc.getKeywords() != null && !doc.getKeywords().isEmpty()
                ? doc.getKeywords()
                : analyzer.extractKeywords(doc.getMarkdownBody(), vesselId);
        log.debug("Keywords for retrieval: {}", keywords);

        List<KnowledgeEntry> relatedEntries = findRelatedEntries(keywords, knowledgeDir);
        log.debug("Found {} related entries", relatedEntries.size());

        AnalysisResult analysis = analyzer.analyze(doc, relatedEntries, context, vesselId);
        log.info("Analysis complete: type={}, confidence={}", analysis.getKnowledgeType(), analysis.getConfidence());

        // HITL：分析结果统一持久化为待审提案，用户确认后通过 knowledgeReview approve 直接落库，无需重跑 LLM。
        KnowledgeProposal proposal = KnowledgeProposal.builder()
                .proposalId(UUID.randomUUID().toString().substring(0, 8))
                .vesselId(vesselId != null && !vesselId.isBlank() ? vesselId : "default")
                .assetId(asset != null ? asset.getAssetId() : null)
                .sha256(asset != null ? asset.getSha256() : null)
                .mediaType(doc.getMediaType())
                .markdownBody(doc.getMarkdownBody())
                .context(context)
                .analysis(analysis)
                .relatedEntryIds(relatedEntries.stream().map(KnowledgeEntry::getId).collect(Collectors.toList()))
                .createdAt(Instant.now().toString())
                .build();

        String preview = renderProposalPreview(proposal, relatedEntries);

        persistProposal(knowledgeDir, proposal);

        ReviewDecision decision = reviewGate != null
                ? reviewGate.review(proposal.getProposalId(), preview)
                : ReviewDecision.PENDING;

        if (decision == ReviewDecision.APPROVED) {
            Map<String, Object> committed = commitProposal(proposal);
            committed.put("review", "approved");
            return committed;
        }

        if (decision == ReviewDecision.REJECTED) {
            deleteProposal(knowledgeDir, proposal.getProposalId());
            result.put("status", "rejected");
            result.put("proposal_id", proposal.getProposalId());
            result.put("message", "Knowledge proposal rejected by reviewer. Nothing was committed.");
            return result;
        }

        result.put("status", "pending_review");
        result.put("proposal_id", proposal.getProposalId());
        result.put("preview", preview);
        result.put("message", "Knowledge proposal is pending review. Use knowledgeReview with proposal_id '" +
                proposal.getProposalId() + "' and decision 'approve' or 'reject'.");
        return result;
    }

    private Map<String, Object> existingProposalResult(Path knowledgeDir, String proposalId, boolean dryRun) {
        KnowledgeProposal proposal = loadProposal(knowledgeDir, proposalId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (proposal == null) {
            result.put("status", "error");
            result.put("message", "Pending proposal not found: " + proposalId);
            return result;
        }
        List<KnowledgeEntry> relatedEntries = proposal.getRelatedEntryIds().stream()
                .map(id -> findEntryById(knowledgeDir, id))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        result.put("status", dryRun
                ? (proposal.getAnalysis() != null ? "analyzed" : "extracted")
                : "pending_review");
        result.put("proposal_id", proposalId);
        result.put("preview", renderProposalPreview(proposal, relatedEntries));
        result.put("content", proposal.getMarkdownBody());
        result.put("analysis", analysisToMap(proposal.getAnalysis()));
        result.put("related_entries", proposal.getRelatedEntryIds());
        result.put("dry_run", dryRun);
        result.put("message", "This asset already has a pending proposal '" + proposalId +
                "' (reused, no re-analysis). Approve or reject it via knowledgeReview.");
        return result;
    }

    private String findPendingProposalByHash(Path knowledgeDir, String sha256) {
        if (sha256 == null || sha256.isBlank()) {
            return null;
        }
        Path pendingDir = knowledgeDir.resolve(".pending");
        if (!Files.exists(pendingDir)) {
            return null;
        }
        try (var stream = Files.list(pendingDir)) {
            return stream.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> loadProposal(knowledgeDir, p.getFileName().toString().replace(".json", "")))
                    .filter(Objects::nonNull)
                    .filter(p -> sha256.equals(p.getSha256()))
                    .map(KnowledgeProposal::getProposalId)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Failed to scan pending proposals: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 异步人审入口：对已持久化的待审提案做 approve/reject 决议。
     */
    public Map<String, Object> resolveProposal(String proposalId, boolean approve) {
        Path knowledgeDir = getKnowledgeDir();
        ensureKnowledgeDir(knowledgeDir);

        Map<String, Object> result = new LinkedHashMap<>();
        KnowledgeProposal proposal = loadProposal(knowledgeDir, proposalId);
        if (proposal == null) {
            result.put("status", "error");
            result.put("message", "Pending proposal not found: " + proposalId);
            return result;
        }

        if (!approve) {
            deleteProposal(knowledgeDir, proposalId);
            result.put("status", "rejected");
            result.put("proposal_id", proposalId);
            result.put("message", "Knowledge proposal rejected. Nothing was committed.");
            return result;
        }

        Map<String, Object> committed = commitProposal(proposal);
        committed.put("review", "approved");
        return committed;
    }

    /**
     * 列出当前待审提案（供用户/通道查询）。
     */
    public List<Map<String, Object>> listPendingProposals() {
        Path pendingDir = getKnowledgeDir().resolve(".pending");
        if (!Files.exists(pendingDir)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> proposals = new ArrayList<>();
        try (var stream = Files.list(pendingDir)) {
            stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                KnowledgeProposal proposal = loadProposal(getKnowledgeDir(),
                        p.getFileName().toString().replace(".json", ""));
                if (proposal != null) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("proposal_id", proposal.getProposalId());
                    m.put("title", proposal.getAnalysis() != null ? proposal.getAnalysis().getSuggestedTitle() : "");
                    m.put("type", proposal.getAnalysis() != null ? proposal.getAnalysis().getKnowledgeType() : "unknown");
                    m.put("confidence", proposal.getAnalysis() != null ? proposal.getAnalysis().getConfidence() : 0.0);
                    m.put("created_at", proposal.getCreatedAt());
                    proposals.add(m);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to list pending proposals: {}", e.getMessage());
        }
        return proposals;
    }

    private Map<String, Object> commitProposal(KnowledgeProposal proposal) {
        Path knowledgeDir = getKnowledgeDir();
        Path vesselDir = getVesselDir();

        ExtractedDocument doc = ExtractedDocument.builder()
                .markdownBody(proposal.getMarkdownBody())
                .mediaType(proposal.getMediaType())
                .build();

        AssetRef asset = null;
        if (proposal.getAssetId() != null) {
            asset = AssetRef.builder()
                    .assetId(proposal.getAssetId())
                    .mediaType(proposal.getMediaType())
                    .sha256(proposal.getSha256())
                    .originalPath(vesselDir.resolve("assets").resolve(proposal.getAssetId())
                            .resolve("original" + extensionForMediaType(proposal.getMediaType())))
                    .build();
        }

        List<KnowledgeEntry> relatedEntries = proposal.getRelatedEntryIds().stream()
                .map(id -> findEntryById(knowledgeDir, id))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // dryRun 提案没有分析结果：approve 时补跑一次统一分析（含矛盾自检），内容无需重新提取
        AnalysisResult analysis = proposal.getAnalysis();
        if (analysis == null) {
            log.info("Proposal {} was extracted without analysis (dryRun); running deferred analysis before commit",
                    proposal.getProposalId());
            List<String> keywords = analyzer.extractKeywords(doc.getMarkdownBody(), proposal.getVesselId());
            relatedEntries = findRelatedEntries(keywords, knowledgeDir);
            analysis = analyzer.analyze(doc, relatedEntries,
                    proposal.getContext() != null ? proposal.getContext() : "", proposal.getVesselId());
            proposal.setAnalysis(analysis);
            proposal.setRelatedEntryIds(relatedEntries.stream().map(KnowledgeEntry::getId).collect(Collectors.toList()));
        }

        Map<String, Object> result = executeAcquire(doc, asset, analysis, relatedEntries, knowledgeDir, vesselDir);

        if ("committed".equals(result.get("status"))) {
            deleteProposal(knowledgeDir, proposal.getProposalId());
            if (assetRegistry != null && proposal.getSha256() != null) {
                assetRegistry.linkKnowledge(proposal.getVesselId(), proposal.getSha256(),
                        String.valueOf(result.get("entry_id")));
            }
        }
        return result;
    }

    private Map<String, Object> alreadyKnownResult(AssetRegistry.AssetRecord record, AssetRef asset,
                                                   Path knowledgeDir, Path vesselDir) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String entryId : record.getKnowledgeEntryIds()) {
            KnowledgeEntry entry = findEntryById(knowledgeDir, entryId);
            if (entry == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", entry.getId());
            m.put("title", entry.getTitle());
            m.put("status", entry.getStatus().getValue());
            if (entry.getSourceFile() != null) {
                m.put("path", vesselDir.relativize(entry.getSourceFile()).toString());
            }
            m.put("snippet", entry.getContent() != null
                    ? entry.getContent().substring(0, Math.min(300, entry.getContent().length()))
                    : "");
            entries.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "already_known");
        result.put("asset_id", asset.getAssetId());
        result.put("entries", entries);
        result.put("message", "This asset was already acquired (" + entries.size() +
                " knowledge entries). Skipped re-analysis. Pass force=true to force re-analysis.");
        return result;
    }

    private KnowledgeEntry findEntryById(Path knowledgeDir, String entryId) {
        if (entryId == null || entryId.isBlank() || !Files.exists(knowledgeDir)) {
            return null;
        }
        try (var stream = Files.walk(knowledgeDir)) {
            return stream.filter(p -> p.toString().endsWith(".md"))
                    .map(this::loadEntry)
                    .filter(Objects::nonNull)
                    .filter(e -> entryId.equals(e.getId()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            log.warn("Failed to search entry {}: {}", entryId, e.getMessage());
            return null;
        }
    }

    private void persistProposal(Path knowledgeDir, KnowledgeProposal proposal) {
        try {
            Path pendingDir = knowledgeDir.resolve(".pending");
            Files.createDirectories(pendingDir);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                    pendingDir.resolve(proposal.getProposalId() + ".json").toFile(), proposal);
        } catch (IOException e) {
            log.error("Failed to persist proposal {}: {}", proposal.getProposalId(), e.getMessage());
        }
    }

    private KnowledgeProposal loadProposal(Path knowledgeDir, String proposalId) {
        Path file = knowledgeDir.resolve(".pending").resolve(proposalId + ".json");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), KnowledgeProposal.class);
        } catch (IOException e) {
            log.error("Failed to load proposal {}: {}", proposalId, e.getMessage());
            return null;
        }
    }

    private void deleteProposal(Path knowledgeDir, String proposalId) {
        try {
            Files.deleteIfExists(knowledgeDir.resolve(".pending").resolve(proposalId + ".json"));
        } catch (IOException e) {
            log.warn("Failed to delete proposal {}: {}", proposalId, e.getMessage());
        }
    }

    private String renderProposalPreview(KnowledgeProposal proposal, List<KnowledgeEntry> relatedEntries) {
        AnalysisResult a = proposal.getAnalysis();
        StringBuilder sb = new StringBuilder();
        sb.append("\n📋 知识库入库提案 ").append(proposal.getProposalId()).append("\n");
        if (a == null) {
            // dryRun 提取态提案：尚未分析，approve 时才补跑分析与矛盾自检
            sb.append("  状态: 已完成内容提取，尚未分析（approve 时补跑分析与矛盾自检）\n");
            String body = proposal.getMarkdownBody() != null ? proposal.getMarkdownBody() : "";
            sb.append("  ---\n");
            sb.append(body, 0, Math.min(600, body.length()));
            if (body.length() > 600) {
                sb.append("\n  ...（正文共 ").append(body.length()).append(" 字符）");
            }
            sb.append("\n");
            return sb.toString();
        }
        sb.append("  标题: ").append(a.getSuggestedTitle() != null && !a.getSuggestedTitle().isEmpty()
                ? a.getSuggestedTitle() : "(未命名)").append("\n");
        sb.append("  类型: ").append(a.getKnowledgeType())
                .append("  |  建议动作: ").append(a.getRecommendedAction())
                .append("  |  置信度: ").append(String.format("%.2f", a.getConfidence()));
        if (a.getConfidence() < 0.9) {
            sb.append(" ⚠️");
        }
        sb.append("\n");
        if (a.getSuggestedTopics() != null && !a.getSuggestedTopics().isEmpty()) {
            sb.append("  主题: ").append(String.join(", ", a.getSuggestedTopics())).append("\n");
        }
        if (a.getContradiction() != null && a.getContradiction().isDetected()) {
            sb.append("  ⚠️ 矛盾: 与条目 ").append(a.getContradiction().getConflictingEntryId())
                    .append(" 冲突——").append(a.getContradiction().getExplanation()).append("\n");
        }
        if (relatedEntries != null && !relatedEntries.isEmpty()) {
            sb.append("  相关条目: ").append(relatedEntries.stream()
                    .map(KnowledgeEntry::getId).collect(Collectors.joining(", "))).append("\n");
        }
        String body = proposal.getMarkdownBody() != null ? proposal.getMarkdownBody() : "";
        sb.append("  ---\n");
        sb.append(body, 0, Math.min(600, body.length()));
        if (body.length() > 600) {
            sb.append("\n  ...（正文共 ").append(body.length()).append(" 字符）");
        }
        sb.append("\n");
        return sb.toString();
    }

    private String extensionForMediaType(String mediaType) {
        if (mediaType == null) return "";
        return switch (mediaType) {
            case "text/plain" -> ".txt";
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "video/url.douyin" -> ".url";
            case "video/mp4" -> ".mp4";
            default -> "";
        };
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

        // 被替代的旧条目：真正改写为 SUPERSEDED 状态并提交（此前只在 memory 中记录）
        for (String supersededId : supersededIds) {
            relatedEntries.stream()
                    .filter(e -> supersededId.equals(e.getId()))
                    .filter(e -> e.getSourceFile() != null)
                    .findFirst()
                    .ifPresent(oldEntry -> {
                        oldEntry.supersede(entryId, commitHash);
                        try {
                            Files.writeString(oldEntry.getSourceFile(), oldEntry.toMarkdown());
                            gitManager.commitKnowledge(oldEntry.getSourceFile(),
                                    "Supersede " + supersededId + " by " + entryId);
                            log.info("Entry {} marked SUPERSEDED by {}", supersededId, entryId);
                        } catch (IOException e) {
                            log.warn("Failed to rewrite superseded entry {}: {}", supersededId, e.getMessage());
                        }
                    });
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
        if (analysis == null) {
            return null;
        }
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