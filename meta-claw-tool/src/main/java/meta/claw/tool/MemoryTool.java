package meta.claw.tool;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.memory.PreferenceMemory;
import meta.claw.core.memory.longterm.LongMemory;
import meta.claw.core.memory.longterm.LongMemoryFactory;
import meta.claw.core.runtime.VesselContext;
import meta.claw.core.tool.annotation.ToolService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 长期记忆用户偏好工具（按 vesselId 隔离）。
 * <p>设计对齐工业界主流方案：</p>
 * <ul>
 *   <li>LangMem：manage/search 工具化读写 + namespace（此处为 vesselId）隔离，
 *   description 明确指导 LLM 何时存、何时不存</li>
 *   <li>mem0：category 分类（preference/fact/context）+ 写入去重 + 可删除纠正</li>
 *   <li>Letta/MemGPT：常驻偏好摘要由 MemorySubSystem 注入 system prompt，
 *   本工具负责按需读写的外部记忆层</li>
 * </ul>
 */
@Slf4j
@ToolService
public class MemoryTool {

    private static final String DEFAULT_CATEGORY = "preference";
    private static final int DEFAULT_SEARCH_LIMIT = 5;
    private static final int DEFAULT_LIST_LIMIT = 20;

    private final LongMemoryFactory longMemoryFactory;

    @Autowired
    public MemoryTool(LongMemoryFactory longMemoryFactory) {
        this.longMemoryFactory = longMemoryFactory;
    }

    private LongMemory store() {
        return longMemoryFactory.get(null);
    }

    @Tool(description = """
            Save a user preference or personal fact to long-term memory (isolated per vessel).
            WHEN TO SAVE: the user explicitly states likes/dislikes, habits, personal traits,
            stable facts (name, role, skills), or long-running project context.
            WHEN NOT TO SAVE: one-off requests, transient task context, or sensitive data
            (passwords, ID numbers, secrets) unless the user explicitly asks.
            Identical content in the same category is deduplicated automatically.
            Categories: preference (likes/dislikes/habits), fact (personal facts), context (project background).""")
    public String memorySave(
            @ToolParam(description = "The preference or fact to remember, as a concise statement, e.g. 'User prefers concise answers'") String content,
            @ToolParam(description = "Category: preference | fact | context. Default: preference", required = false) String category) {

        if (content == null || content.isBlank()) {
            return "Error: content is required for memorySave";
        }

        String vesselId = VesselContext.getVesselId();
        String cat = category != null && !category.isBlank() ? category.trim() : DEFAULT_CATEGORY;

        // 写入去重（mem0 ADD 语义的轻量版）：同 category 且归一化 content 相同视为已存在
        String normalized = normalize(content);
        for (PreferenceMemory existing : store().listRecentPreferences(vesselId, 0)) {
            if (cat.equals(existing.getCategory()) && normalized.equals(normalize(existing.getContent()))) {
                return "Already remembered (id: " + existing.getId() + "): " + existing.getContent();
            }
        }

        PreferenceMemory entry = PreferenceMemory.builder()
                .id(UUID.randomUUID().toString().substring(0, 8))
                .timestamp(LocalDateTime.now())
                .category(cat)
                .content(content.trim())
                .metadata(Map.of("source", "user"))
                .build();
        store().addPreference(vesselId, entry);
        log.info("Saved preference {} for vessel {} (category={})", entry.getId(), vesselId, cat);
        return "Saved to long-term memory (id: " + entry.getId() + ", category: " + cat + "): " + entry.getContent();
    }

    @Tool(description = """
            Search long-term user preferences and facts. Use this when answering depends on
            the user's prior preferences, identity, habits, or history that may have been
            saved in earlier sessions.""")
    public String memorySearch(
            @ToolParam(description = "Search query (keyword, e.g. 'Python', 'answer style')") String query,
            @ToolParam(description = "Optional category filter: preference | fact | context", required = false) String category,
            @ToolParam(description = "Max results to return. Default: 5", required = false) Integer maxResults) {

        if (query == null || query.isBlank()) {
            return "Error: query is required for memorySearch";
        }

        String vesselId = VesselContext.getVesselId();
        int limit = maxResults != null && maxResults > 0 ? maxResults : DEFAULT_SEARCH_LIMIT;

        List<PreferenceMemory> hits = store().lookupPreference(vesselId, query.trim()).stream()
                .filter(e -> category == null || category.isBlank() || category.trim().equals(e.getCategory()))
                .limit(limit)
                .toList();

        if (hits.isEmpty()) {
            return "No saved preferences or facts match '" + query + "'.";
        }
        return formatEntries("Matching memories", hits);
    }

    @Tool(description = "List recently saved long-term preferences/facts (with ids, newest last). Useful before memoryDelete.")
    public String memoryList(
            @ToolParam(description = "Optional category filter: preference | fact | context", required = false) String category,
            @ToolParam(description = "Max entries to return. Default: 20", required = false) Integer limit) {

        String vesselId = VesselContext.getVesselId();
        int lim = limit != null && limit > 0 ? limit : DEFAULT_LIST_LIMIT;

        List<PreferenceMemory> entries = store().listRecentPreferences(vesselId, lim).stream()
                .filter(e -> category == null || category.isBlank() || category.trim().equals(e.getCategory()))
                .toList();

        if (entries.isEmpty()) {
            return "No long-term memories saved yet.";
        }
        return formatEntries("Saved memories (" + entries.size() + ")", entries);
    }

    @Tool(description = """
            Delete a saved preference/fact by id (use memoryList to find ids).
            Use when the user asks to forget or correct something previously remembered.""")
    public String memoryDelete(
            @ToolParam(description = "The preference id to delete") String preferenceId) {

        if (preferenceId == null || preferenceId.isBlank()) {
            return "Error: preferenceId is required for memoryDelete";
        }

        String vesselId = VesselContext.getVesselId();
        boolean exists = store().listRecentPreferences(vesselId, 0).stream()
                .anyMatch(e -> preferenceId.trim().equals(e.getId()));
        if (!exists) {
            return "Error: preference not found: " + preferenceId;
        }

        boolean deleted = store().deletePreference(vesselId, preferenceId.trim());
        return deleted
                ? "Deleted preference " + preferenceId + " from long-term memory."
                : "Error: failed to delete preference " + preferenceId;
    }

    private String formatEntries(String heading, List<PreferenceMemory> entries) {
        StringBuilder sb = new StringBuilder(heading).append(":\n");
        for (PreferenceMemory e : entries) {
            sb.append("- [").append(e.getCategory() != null ? e.getCategory() : DEFAULT_CATEGORY)
                    .append("] (id: ").append(e.getId()).append(") ")
                    .append(e.getContent()).append("\n");
        }
        return sb.toString();
    }

    private String normalize(String content) {
        return content == null ? "" : content.trim().toLowerCase(Locale.ROOT);
    }
}
