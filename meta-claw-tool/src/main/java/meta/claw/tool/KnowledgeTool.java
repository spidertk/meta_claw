package meta.claw.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.runtime.VesselContext;
import meta.claw.core.tool.annotation.ToolService;
import meta.claw.core.knowledge.GitManager;
import meta.claw.core.knowledge.KnowledgeManager;
import meta.claw.core.knowledge.source.KnowledgeSource;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@ToolService
public class KnowledgeTool {

    private final KnowledgeManager knowledgeManager;
    private final GitManager gitManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public KnowledgeTool(KnowledgeManager knowledgeManager, GitManager gitManager) {
        this.knowledgeManager = knowledgeManager;
        this.gitManager = gitManager;
    }

    @Tool(description = """
            Save knowledge with automatic classification and contradiction detection.
            Knowledge is stored per-vessel at .meta-claw/vessels/{vesselId}/knowledge/.
            The knowledge system maintains a time-truth model: new facts replace old ones when
            contradictions are detected, with full history preserved in Git.
            Semantic contradiction detection uses LLM to identify conflicts at the meaning level,
            not just keyword matching.""")
    public String knowledgeAcquire(
            @ToolParam(description = "Full knowledge content in markdown format") String content,
            @ToolParam(description = "Additional context to help with classification and contradiction detection", required = false) String context,
            @ToolParam(description = "If true, only prepare the knowledge without committing: analysis/contradiction check is skipped and runs later at approval", required = false) Boolean dryRun) {

        if (content == null || content.isBlank()) {
            return "Error: content is required for acquire";
        }

        String ctx = context != null ? context : "";
        boolean dry = dryRun != null && dryRun;

        KnowledgeSource source = KnowledgeSource.builder()
                .mediaType("text/plain")
                .content(content)
                .build();

        Map<String, Object> result = knowledgeManager.acquire(source, ctx, dry);

        return formatAcquireResult(result);
    }

    @Tool(description = """
            Acquire knowledge from a local file (image, PDF, etc.).
            Assets are deduplicated by content hash: if the same file was acquired before,
            existing knowledge is returned directly without re-analysis.
            Non-dry-run acquisition produces a proposal that requires human review
            (approve/reject via knowledgeReview) before it is committed to the knowledge base.""")
    public String knowledgeAcquireFromFile(
            @ToolParam(description = "Absolute or vessel-relative file path") String filePath,
            @ToolParam(description = "Optional context", required = false) String context,
            @ToolParam(description = "If true, only run content extraction (e.g. image recognition) without analysis/contradiction check or committing; the full extracted content and a reusable pending proposal_id are returned, so the result can be approved later via knowledgeReview (analysis runs once at approval, no re-extraction needed)", required = false) Boolean dryRun,
            @ToolParam(description = "If true, force re-analysis even if this asset was acquired before", required = false) Boolean force) {

        if (filePath == null || filePath.isBlank()) {
            return "Error: filePath is required";
        }

        Path path = Path.of(filePath);
        if (!path.isAbsolute()) {
            path = ProjectRootFinder.getMetaClawDir()
                    .resolve("vessels")
                    .resolve(VesselContext.getVesselId())
                    .resolve(filePath);
        }

        if (!Files.exists(path)) {
            return "Error: file not found: " + filePath;
        }

        String mediaType = inferMediaType(filePath);
        KnowledgeSource source = KnowledgeSource.builder()
                .mediaType(mediaType)
                .uri(path.toUri())
                .originalName(path.getFileName().toString())
                .build();

        Map<String, Object> result = knowledgeManager.acquire(source, context != null ? context : "",
                dryRun != null && dryRun, force != null && force);
        return formatAcquireResult(result);
    }

    @Tool(description = "Acquire knowledge from a URL (currently Douyin prioritized)")
    public String knowledgeAcquireFromUrl(
            @ToolParam(description = "Source URL") String url,
            @ToolParam(description = "Optional context", required = false) String context,
            @ToolParam(description = "If true, only run content extraction without analysis/contradiction check or committing; analysis runs later at approval", required = false) Boolean dryRun,
            @ToolParam(description = "If true, force re-analysis even if this asset was acquired before", required = false) Boolean force) {

        if (url == null || url.isBlank()) {
            return "Error: url is required";
        }

        String mediaType = inferMediaType(url);
        if (!"video/url.douyin".equals(mediaType)) {
            return "Error: unsupported URL type: " + url;
        }

        KnowledgeSource source = KnowledgeSource.builder()
                .mediaType(mediaType)
                .uri(URI.create(url))
                .originalName("douyin_link")
                .build();

        Map<String, Object> result = knowledgeManager.acquire(source, context != null ? context : "",
                dryRun != null && dryRun, force != null && force);
        return formatAcquireResult(result);
    }

    @Tool(description = """
            Review a pending knowledge proposal (human-in-the-loop).
            Every non-dry-run acquisition first creates a pending proposal; nothing is
            committed until it is approved here. Use 'approve' to commit it into the
            knowledge base, or 'reject' to discard it.""")
    public String knowledgeReview(
            @ToolParam(description = "Pending proposal ID returned by knowledgeAcquire*") String proposalId,
            @ToolParam(description = "'approve' to commit, 'reject' to discard") String decision) {

        if (proposalId == null || proposalId.isBlank()) {
            List<Map<String, Object>> pending = knowledgeManager.listPendingProposals();
            if (pending.isEmpty()) {
                return "No pending knowledge proposals.";
            }
            StringBuilder sb = new StringBuilder("Pending knowledge proposals:\n\n");
            for (Map<String, Object> p : pending) {
                sb.append("- ").append(p.get("proposal_id"))
                        .append(": ").append(p.getOrDefault("title", "(untitled)"))
                        .append(" (type=").append(p.get("type"))
                        .append(", confidence=").append(String.format("%.2f", ((Number) p.get("confidence")).doubleValue()))
                        .append(")\n");
            }
            return sb.toString();
        }

        boolean approve = "approve".equalsIgnoreCase(decision != null ? decision.trim() : "");
        if (!approve && !"reject".equalsIgnoreCase(decision != null ? decision.trim() : "")) {
            return "Error: decision must be 'approve' or 'reject'";
        }

        Map<String, Object> result = knowledgeManager.resolveProposal(proposalId.trim(), approve);
        return formatAcquireResult(result);
    }

    private String inferMediaType(String pathOrUrl) {
        String lower = pathOrUrl.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.contains("douyin.com") || lower.contains("iesdouyin.com")) return "video/url.douyin";
        return "text/plain";
    }

    @Tool(description = """
            Search knowledge with multiple modes.
            Knowledge is scoped to the current vessel.
            Mode 'current' returns active knowledge, 'history' returns evolution context
            including full commit history.""")
    public String knowledgeRetrieve(
            @ToolParam(description = "Search query/terms to find relevant knowledge") String query,
            @ToolParam(description = "'current' returns active knowledge, 'history' returns evolution context", required = false) String mode,
            @ToolParam(description = "Maximum number of results to return", required = false) Integer maxResults) {

        if (query == null || query.isBlank()) {
            return "Error: query is required for retrieve";
        }

        String m = mode != null ? mode : "current";
        int max = maxResults != null ? maxResults : 5;

        List<Map<String, Object>> results = knowledgeManager.retrieve(query, m, max);

        if (results.isEmpty()) {
            return "No knowledge found for query: '" + query + "'";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(results.size()).append(" relevant entries for '").append(query).append("' (mode=").append(m).append("):\n\n");

        int idx = 1;
        for (Map<String, Object> r : results) {
            sb.append(idx).append(". ").append(r.getOrDefault("title", "Untitled")).append("\n");
            sb.append("   Path: ").append(r.getOrDefault("path", "unknown")).append("\n");

            if (r.containsKey("type")) {
                sb.append("   Type: ").append(r.get("type")).append("\n");
            }
            if (r.containsKey("status")) {
                sb.append("   Status: ").append(r.get("status")).append("\n");
            }
            if (r.containsKey("media_type")) {
                sb.append("   Media: ").append(r.get("media_type")).append("\n");
            }
            if (r.containsKey("source_asset")) {
                sb.append("   Asset: ").append(r.get("source_asset")).append("\n");
            }

            if ("history".equals(m) && r.containsKey("history")) {
                sb.append("   History:\n");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> history = (List<Map<String, Object>>) r.get("history");
                if (history != null) {
                    for (Map<String, Object> h : history) {
                        sb.append("     - ").append(h.getOrDefault("commit", "unknown")).append(": ");
                        String msg = String.valueOf(h.getOrDefault("message", ""));
                        sb.append(msg, 0, Math.min(50, msg.length())).append("\n");
                    }
                }
            } else {
                Object snippet = r.get("snippet");
                if (snippet != null) {
                    String s = String.valueOf(snippet);
                    sb.append("   ").append(s).append("...\n");
                }
            }
            sb.append("\n");
            idx++;
        }

        return sb.toString();
    }

    @Tool(description = "Get full content of a knowledge file (scoped to current vessel)")
    public String knowledgeRead(
            @ToolParam(description = "Path to the knowledge file (relative to vessel directory)") String path) {

        if (path == null || path.isBlank()) {
            return "Error: path is required for read";
        }

        String content = knowledgeManager.getFull(path);
        if (content == null) {
            return "Error: Knowledge file not found: " + path;
        }

        return content;
    }

    @Tool(description = "List all knowledge files in the current vessel")
    public String knowledgeList() {
        List<Map<String, Object>> files = knowledgeManager.listAll();

        if (files.isEmpty()) {
            return "No knowledge files found.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Knowledge files (").append(files.size()).append(" total):\n\n");

        for (Map<String, Object> f : files) {
            long size = f.get("size") instanceof Number n ? n.longValue() : 0;
            sb.append("- ").append(f.getOrDefault("title", "Untitled")).append(" (").append(String.format("%.1f", size / 1024.0)).append(" KB)\n");
            sb.append("  Path: ").append(f.getOrDefault("path", "unknown")).append("\n");
            sb.append("  Type: ").append(f.getOrDefault("type", "unknown")).append(", Status: ").append(f.getOrDefault("status", "unknown")).append("\n");
        }

        return sb.toString();
    }

    @Tool(description = "View commit history of knowledge evolution (scoped to current vessel)")
    public String knowledgeHistory(
            @ToolParam(description = "Path to the knowledge file (relative to vessel directory)") String path) {

        if (path == null || path.isBlank()) {
            return "Error: path is required for history";
        }

        List<Map<String, Object>> history = knowledgeManager.getHistory(path);

        if (history.isEmpty()) {
            return "No history found for: " + path;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("History of ").append(path).append(":\n\n");

        int idx = 1;
        for (Map<String, Object> commit : history) {
            sb.append(idx).append(". ").append(commit.getOrDefault("commit", "unknown")).append("\n");
            sb.append("   Date: ").append(commit.getOrDefault("date", "unknown")).append("\n");
            sb.append("   Author: ").append(commit.getOrDefault("author", "unknown")).append("\n");
            sb.append("   Message: ").append(commit.getOrDefault("message", "")).append("\n");
            sb.append("\n");
            idx++;
        }

        return sb.toString();
    }

    @Tool(description = "Create/switch experiment branches for knowledge (scoped to current vessel)")
    public String knowledgeBranch(
            @ToolParam(description = "Name of the branch") String branchName,
            @ToolParam(description = "'create', 'switch', 'merge', or 'list'", required = false) String action) {

        String act = action != null ? action : "list";

        return switch (act) {
            case "create" -> {
                if (branchName == null || branchName.isBlank()) {
                    yield "Error: branch_name is required for create";
                }
                boolean success = gitManager.createBranch(branchName, "HEAD");
                yield success ? "Branch '" + branchName + "' created" : "Failed to create branch '" + branchName + "'";
            }
            case "switch" -> {
                if (branchName == null || branchName.isBlank()) {
                    yield "Error: branch_name is required for switch";
                }
                boolean success = gitManager.checkoutBranch(branchName);
                yield success ? "Switched to branch '" + branchName + "'" : "Failed to switch to branch '" + branchName + "'";
            }
            case "merge" -> {
                if (branchName == null || branchName.isBlank()) {
                    yield "Error: branch_name is required for merge";
                }
                boolean success = gitManager.mergeBranch(branchName, null);
                yield success ? "Merged branch '" + branchName + "'" : "Merge failed";
            }
            case "list" -> {
                List<String> branches = gitManager.listBranches();
                String current = gitManager.getCurrentBranch();
                StringBuilder sb = new StringBuilder("Knowledge branches:\n\n");
                for (String b : branches) {
                    sb.append(b.equals(current) ? "* " : "  ").append(b).append("\n");
                }
                yield sb.toString();
            }
            default -> "Error: Unknown branch action '" + act + "'";
        };
    }

    private String formatAcquireResult(Map<String, Object> result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Knowledge Acquisition Result\n");

        String status = String.valueOf(result.getOrDefault("status", "unknown"));
        sb.append("Status: ").append(status).append("\n");

        switch (status) {
            case "pending_review" -> {
                sb.append("Proposal ID: ").append(result.getOrDefault("proposal_id", "unknown")).append("\n");
                if (result.containsKey("preview")) {
                    sb.append(result.get("preview"));
                }
            }
            case "analyzed", "extracted" -> {
                sb.append("Proposal ID: ").append(result.getOrDefault("proposal_id", "unknown")).append("\n");
                if (result.containsKey("preview")) {
                    sb.append(result.get("preview"));
                }
                // dryRun 也要把完整提取内容返回给主 agent，否则视觉/分析调用结果被浪费
                Object content = result.get("content");
                if (content != null) {
                    String c = String.valueOf(content);
                    sb.append("\nExtracted Content (full):\n");
                    sb.append(c, 0, Math.min(8000, c.length()));
                    if (c.length() > 8000) {
                        sb.append("\n...（内容共 ").append(c.length()).append(" 字符，已截断）");
                    }
                    sb.append("\n");
                }
            }
            case "already_known" -> {
                sb.append("Asset ID: ").append(result.getOrDefault("asset_id", "unknown")).append("\n");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entries = (List<Map<String, Object>>) result.get("entries");
                if (entries != null) {
                    for (Map<String, Object> e : entries) {
                        sb.append("  - [ ").append(e.get("id")).append("] ")
                                .append(e.getOrDefault("title", "Untitled"))
                                .append(" (").append(e.getOrDefault("status", "unknown")).append(")\n");
                        if (e.containsKey("path")) {
                            sb.append("    Path: ").append(e.get("path")).append("\n");
                        }
                    }
                }
            }
            case "rejected" -> sb.append("The proposal was rejected. Nothing was committed.\n");
            default -> { }
        }

        if (result.containsKey("analysis")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> analysis = (Map<String, Object>) result.get("analysis");
            if (analysis != null) {
                sb.append("\nClassification: ").append(analysis.getOrDefault("knowledgeType", "unknown")).append("\n");
                sb.append("Confidence: ").append(String.format("%.2f", getDouble(analysis, "confidence"))).append("\n");

                String title = String.valueOf(analysis.getOrDefault("suggestedTitle", ""));
                if (!title.isEmpty()) {
                    sb.append("Title: ").append(title).append("\n");
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> contradiction = (Map<String, Object>) analysis.get("contradiction");
                if (contradiction != null && Boolean.TRUE.equals(contradiction.get("detected"))) {
                    sb.append("\nContradiction Detected:\n");
                    sb.append("  Conflicting entry: ").append(contradiction.getOrDefault("conflictingEntryId", "unknown")).append("\n");
                    sb.append("  Explanation: ").append(contradiction.getOrDefault("explanation", "")).append("\n");
                    sb.append("  Confidence: ").append(String.format("%.2f", getDouble(contradiction, "confidence"))).append("\n");
                }
            }
        }

        if (result.containsKey("superseded_ids")) {
            @SuppressWarnings("unchecked")
            List<String> superseded = (List<String>) result.get("superseded_ids");
            if (superseded != null && !superseded.isEmpty()) {
                sb.append("\nSuperseded entries: ").append(String.join(", ", superseded)).append("\n");
            }
        }

        if (result.containsKey("commit_hash")) {
            String hash = String.valueOf(result.get("commit_hash"));
            sb.append("\nCommitted: ").append(hash, 0, Math.min(8, hash.length())).append("\n");
            sb.append("  File: ").append(result.getOrDefault("file_path", "unknown")).append("\n");
            sb.append("  Entry ID: ").append(result.getOrDefault("entry_id", "unknown")).append("\n");
        }

        if (result.containsKey("message")) {
            sb.append("\n").append(result.get("message"));
        }

        return sb.toString();
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        return 0.0;
    }
}