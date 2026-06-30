package meta.claw.core.knowledge;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiLlmClient;
import meta.claw.core.runtime.VesselContext;
import meta.claw.core.knowledge.GitManager;
import meta.claw.core.knowledge.KnowledgeAnalyzer;
import meta.claw.core.knowledge.KnowledgeManager;
import meta.claw.core.knowledge.multimodal.ModelCapability;
import meta.claw.core.knowledge.multimodal.MultimodalConfig;
import meta.claw.core.knowledge.asset.AssetManager;
import meta.claw.core.knowledge.asset.LocalAssetManager;
import meta.claw.core.knowledge.extract.ContentExtractorService;
import meta.claw.core.knowledge.extract.TextExtractor;
import meta.claw.core.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeToolTest {

    @TempDir
    Path tempDir;

    private KnowledgeTool tool;
    private SpiLlmClient mockLlm;
    private String originalUserDir;

    private static final String MOCK_LLM_RESPONSE = """
            {
                "knowledge_type": "fact",
                "is_fact": true,
                "contradiction": {
                    "detected": false,
                    "conflicting_entry_id": "",
                    "explanation": "",
                    "confidence": 0.0,
                    "contradiction_type": ""
                },
                "confidence": 0.95,
                "recommended_action": "add",
                "reasoning": "This is a well-known technical fact",
                "extracted_keywords": ["Python", "3.9", "release"],
                "suggested_topics": ["python"],
                "suggested_title": "Python 3.9 Release Date",
                "commit_summary": "Add knowledge: Python 3.9 Release Date",
                "commit_description": ""
            }
            """;

    @BeforeEach
    void setUp() throws Exception {
        originalUserDir = System.getProperty("user.dir");

        // Redirect user.dir to tempDir so ProjectRootFinder resolves
        // .meta-claw/vessels/test-vessel/knowledge/ inside tempDir
        System.setProperty("user.dir", tempDir.toString());

        // Create the knowledge directory that KnowledgeManager expects
        // getKnowledgeDir() = getMetaClawDir().resolve("vessels").resolve(vesselId).resolve("knowledge")
        Path knowledgeDir = tempDir.resolve(".meta-claw").resolve("vessels").resolve("test-vessel").resolve("knowledge");
        Files.createDirectories(knowledgeDir);

        VesselContext.setVesselId("test-vessel");

        // Mock LLM: return a high-confidence "add" response
        mockLlm = mock(SpiLlmClient.class);
        when(mockLlm.chat(any(SpiChatRequest.class)))
                .thenReturn(SpiChatResponse.builder().content(MOCK_LLM_RESPONSE).build());

        // Build real components with mocked LLM
        GitManager gitManager = new GitManager();
        gitManager.init(knowledgeDir);

        MultimodalConfig multimodalConfig = new MultimodalConfig();
        ModelCapability modelCapability = new ModelCapability(multimodalConfig);
        KnowledgeAnalyzer analyzer = new KnowledgeAnalyzer(mockLlm, modelCapability);
        ContentExtractorService extractorService = new ContentExtractorService(List.of(new TextExtractor()));
        AssetManager assetManager = new LocalAssetManager();
        KnowledgeManager knowledgeManager = new KnowledgeManager(gitManager, analyzer, extractorService, assetManager);

        tool = new KnowledgeTool(knowledgeManager, gitManager);
    }

    @AfterEach
    void tearDown() {
        VesselContext.clear();
        System.setProperty("user.dir", originalUserDir);
    }

    // ========== knowledgeAcquire ==========

    @Test
    void acquireAddsNewKnowledge() {
        String result = tool.knowledgeAcquire(
                "Python 3.9 was released on October 5, 2020.",
                null,
                null);

        assertTrue(result.contains("Committed"), "Expected committed status, got: " + result);
        assertTrue(result.contains("Python 3.9 Release Date"), "Expected title in result, got: " + result);
    }

    @Test
    void acquireDryRunDoesNotCommit() {
        String result = tool.knowledgeAcquire(
                "Python 3.9 was released on October 5, 2020.",
                null,
                true);

        assertTrue(result.contains("Status: analyzed"), "Expected analyzed status, got: " + result);
    }

    @Test
    void acquireRejectsEmptyContent() {
        String result = tool.knowledgeAcquire("", null, null);
        assertTrue(result.startsWith("Error"), "Expected error for empty content, got: " + result);
    }

    @Test
    void textAcquireRecordsAssetMetadata() {
        tool.knowledgeAcquire("Java 21 was released in September 2023.", null, false);

        String listResult = tool.knowledgeList();
        String path = extractPath(listResult);
        assertNotNull(path, "Could not extract path from list output: " + listResult);

        String content = tool.knowledgeRead(path);
        assertTrue(content.contains("media_type: text/plain"), "Expected media_type frontmatter, got: " + content);
        assertTrue(content.contains("multimodal_used: false"), "Expected multimodal_used frontmatter, got: " + content);
    }

    @Test
    void acquireFromFileRejectsMissingFile() {
        String result = tool.knowledgeAcquireFromFile("/nonexistent/file.pdf", null, null);
        assertTrue(result.startsWith("Error"), "Expected error for missing file, got: " + result);
    }

    @Test
    void acquireFromUrlRejectsUnsupportedHost() {
        String result = tool.knowledgeAcquireFromUrl("https://example.com/video", null, null);
        assertTrue(result.startsWith("Error"), "Expected error for unsupported URL, got: " + result);
    }

    // ========== knowledgeRetrieve ==========

    @Test
    void retrieveReturnsNoContentForEmptyRepo() {
        String result = tool.knowledgeRetrieve("Python", null, null);
        assertTrue(result.contains("No knowledge found"), "Expected no results, got: " + result);
    }

    @Test
    void retrieveReturnsResultsAfterAcquire() {
        tool.knowledgeAcquire("Python 3.9 was released on October 5, 2020.", null, null);

        String result = tool.knowledgeRetrieve("Python", null, null);
        assertTrue(result.contains("Found 1 relevant entries"), "Expected 1 result, got: " + result);
        assertTrue(result.contains("Python 3.9 Release Date"), "Expected title in search results, got: " + result);
    }

    @Test
    void retrieveRejectsEmptyQuery() {
        String result = tool.knowledgeRetrieve("", null, null);
        assertTrue(result.startsWith("Error"), "Expected error for empty query, got: " + result);
    }

    @Test
    void retrieveIncludesAssetMetadata() {
        tool.knowledgeAcquire("Java 21 was released in September 2023.", null, false);
        String result = tool.knowledgeRetrieve("Java", "current", 5);
        assertTrue(result.contains("Media: text/plain"), "Expected media_type in retrieve result, got: " + result);
    }

    // ========== knowledgeList ==========

    @Test
    void listReturnsEmptyForEmptyRepo() {
        String result = tool.knowledgeList();
        assertTrue(result.contains("No knowledge files found"), "Expected empty list, got: " + result);
    }

    @Test
    void listShowsFilesAfterAcquire() {
        tool.knowledgeAcquire("Python 3.9 was released on October 5, 2020.", null, null);

        String result = tool.knowledgeList();
        assertTrue(result.contains("1 total"), "Expected 1 file, got: " + result);
        assertTrue(result.contains("Python 3.9 Release Date"), "Expected file title in list, got: " + result);
    }

    // ========== knowledgeRead ==========

    @Test
    void readReturnsFileContent() {
        tool.knowledgeAcquire("Python 3.9 was released on October 5, 2020.", null, null);

        String listResult = tool.knowledgeList();
        String path = extractPath(listResult);
        assertNotNull(path, "Could not extract path from list output: " + listResult);

        String content = tool.knowledgeRead(path);
        assertTrue(content.contains("Python 3.9"), "Expected knowledge content, got: " + content);
        assertTrue(content.contains("id:"), "Expected YAML frontmatter, got: " + content);
    }

    @Test
    void readRejectsEmptyPath() {
        String result = tool.knowledgeRead("");
        assertTrue(result.startsWith("Error"), "Expected error for empty path, got: " + result);
    }

    // ========== knowledgeHistory ==========

    @Test
    void historyReturnsEmptyForUnknownPath() {
        String result = tool.knowledgeHistory("nonexistent.md");
        assertTrue(result.contains("No history found"), "Expected no history, got: " + result);
    }

    @Test
    void historyShowsCommitsAfterAcquire() {
        tool.knowledgeAcquire("Python 3.9 was released on October 5, 2020.", null, null);

        String listResult = tool.knowledgeList();
        String path = extractPath(listResult);
        assertNotNull(path, "Could not extract path");

        String result = tool.knowledgeHistory(path);
        assertTrue(result.contains("1."), "Expected at least 1 commit, got: " + result);
        assertTrue(result.contains("Add knowledge"), "Expected commit message, got: " + result);
    }

    // ========== knowledgeBranch ==========

    @Test
    void branchListReturnsAtLeastMaster() {
        String result = tool.knowledgeBranch(null, "list");
        assertFalse(result.startsWith("Error"), "Expected branch list, got error: " + result);
        assertTrue(result.contains("Knowledge branches"), "Expected branch list header, got: " + result);
    }

    @Test
    void branchCreateAndSwitch() {
        // Need at least one commit for branch operations
        tool.knowledgeAcquire("Python 3.9 was released on October 5, 2020.", null, null);

        String createResult = tool.knowledgeBranch("experiment", "create");
        assertTrue(createResult.contains("created"), "Expected branch created, got: " + createResult);

        String switchResult = tool.knowledgeBranch("experiment", "switch");
        assertTrue(switchResult.contains("Switched"), "Expected switch success, got: " + switchResult);

        // Switch back to original branch
        String listResult = tool.knowledgeBranch(null, "list");
        String originalBranch = "";
        for (String line : listResult.split("\n")) {
            if (line.startsWith("* ")) {
                originalBranch = line.substring(2).trim();
                break;
            }
        }
        assertTrue(originalBranch.contains("experiment"), "Should be on 'experiment', but is on: " + originalBranch);
    }

    // ========== Helpers ==========

    private String extractPath(String listOutput) {
        for (String line : listOutput.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("Path:")) {
                return trimmed.substring(5).trim();
            }
        }
        return null;
    }
}