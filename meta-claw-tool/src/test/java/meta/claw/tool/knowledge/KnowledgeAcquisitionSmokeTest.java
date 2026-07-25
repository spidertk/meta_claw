package meta.claw.tool.knowledge;

import meta.claw.core.knowledge.GitManager;
import meta.claw.core.knowledge.KnowledgeAnalyzer;
import meta.claw.core.knowledge.KnowledgeManager;
import meta.claw.core.knowledge.asset.AssetManager;
import meta.claw.core.knowledge.asset.AssetRegistry;
import meta.claw.core.knowledge.extract.*;
import meta.claw.core.knowledge.review.ReviewDecision;
import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiLlmClient;
import meta.claw.core.runtime.VesselContext;
import meta.claw.tool.KnowledgeTool;
import meta.claw.core.knowledge.asset.LocalAssetManager;
import meta.claw.core.knowledge.multimodal.ModelCapability;
import meta.claw.core.knowledge.multimodal.MultimodalConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeAcquisitionSmokeTest {

    @TempDir
    Path tempDir;

    private KnowledgeTool tool;
    private KnowledgeManager knowledgeManager;
    private SpiLlmClient mockLlm;
    private String originalUserDir;

    private static final String MOCK_VISION_RESPONSE = """
            {"description": "一张测试图片", "keywords": ["Test", "Image"]}
            """;

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
                "extracted_keywords": ["Test"],
                "suggested_topics": ["smoke"],
                "suggested_title": "Smoke Test",
                "commit_summary": "Add smoke test",
                "commit_description": ""
            }
            """;

    @BeforeEach
    void setUp() throws Exception {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
        Files.createDirectories(tempDir.resolve(".meta-claw/vessels/smoke/knowledge"));
        VesselContext.setVesselId("smoke");

        mockLlm = mock(SpiLlmClient.class);
        when(mockLlm.chat(any(SpiChatRequest.class)))
                .thenReturn(SpiChatResponse.builder().content(MOCK_LLM_RESPONSE).build());

        GitManager gitManager = new GitManager();
        gitManager.init(tempDir.resolve(".meta-claw/vessels/smoke/knowledge"));

        AssetRegistry assetRegistry = new AssetRegistry();
        AssetManager assetManager = new LocalAssetManager(assetRegistry);
        VisionDescriber visionDescriber = new VisionDescriber(mockLlm);
        ContentExtractorService extractorService = new ContentExtractorService(List.of(
                new TextExtractor(),
                new ImageExtractor(visionDescriber),
                new PdfExtractor(visionDescriber)
        ));
        KnowledgeAnalyzer analyzer = new KnowledgeAnalyzer(mockLlm, new ModelCapability(new MultimodalConfig()));
        knowledgeManager = new KnowledgeManager(gitManager, analyzer, extractorService, assetManager);
        knowledgeManager.setAssetRegistry(assetRegistry);
        // 默认自动批准，保持既有「直接落库」测试语义
        knowledgeManager.setReviewGate((proposalId, preview) -> ReviewDecision.APPROVED);

        tool = new KnowledgeTool(knowledgeManager, gitManager);
    }

    @AfterEach
    void tearDown() {
        VesselContext.clear();
        System.setProperty("user.dir", originalUserDir);
    }

    @Test
    void textAcquisitionCommitsEntry() {
        String result = tool.knowledgeAcquire("Smoke test content", null, false);
        assertTrue(result.contains("Committed"), "Expected committed status, got: " + result);
    }

    @Test
    void imageAcquisitionCommitsEntry() throws Exception {
        Path imagePath = createTestImage();

        String result = tool.knowledgeAcquireFromFile(imagePath.toString(), null, false, false);
        assertTrue(result.contains("Committed"), "Expected committed status for image, got: " + result);
    }

    @Test
    void imageAcquisitionUsesTwoLlmCallsOnly() throws Exception {
        // 第一次调用：视觉理解（描述+关键词合一）；第二次：统一知识分析
        reset(mockLlm);
        when(mockLlm.chat(any(SpiChatRequest.class)))
                .thenReturn(SpiChatResponse.builder().content(MOCK_VISION_RESPONSE).build())
                .thenReturn(SpiChatResponse.builder().content(MOCK_LLM_RESPONSE).build());

        Path imagePath = createTestImage();
        String result = tool.knowledgeAcquireFromFile(imagePath.toString(), null, false, false);

        assertTrue(result.contains("Committed"), "Expected committed status, got: " + result);
        verify(mockLlm, times(2)).chat(any(SpiChatRequest.class));
    }

    @Test
    void duplicateAssetReturnsAlreadyKnownWithoutLlmCalls() throws Exception {
        reset(mockLlm);
        when(mockLlm.chat(any(SpiChatRequest.class)))
                .thenReturn(SpiChatResponse.builder().content(MOCK_VISION_RESPONSE).build())
                .thenReturn(SpiChatResponse.builder().content(MOCK_LLM_RESPONSE).build());

        Path imagePath = createTestImage();
        String first = tool.knowledgeAcquireFromFile(imagePath.toString(), null, false, false);
        assertTrue(first.contains("Committed"), "First acquire should commit, got: " + first);
        int callsAfterFirst = mockingDetails(mockLlm).getInvocations().size();

        String second = tool.knowledgeAcquireFromFile(imagePath.toString(), null, false, false);
        assertTrue(second.contains("already_known"), "Second acquire should hit already_known, got: " + second);
        // 第二次采集零 LLM 调用
        assertEquals(callsAfterFirst, mockingDetails(mockLlm).getInvocations().size(),
                "Duplicate asset must not trigger any LLM call");

        // 资产目录不重复
        Path assetsDir = tempDir.resolve(".meta-claw/vessels/smoke/assets");
        long assetDirs = Files.list(assetsDir).filter(Files::isDirectory).count();
        assertEquals(1, assetDirs, "Duplicate asset must not create a second asset directory");
    }

    @Test
    void forceReanalysisBypassesAlreadyKnown() throws Exception {
        reset(mockLlm);
        when(mockLlm.chat(any(SpiChatRequest.class)))
                .thenReturn(SpiChatResponse.builder().content(MOCK_VISION_RESPONSE).build())
                .thenReturn(SpiChatResponse.builder().content(MOCK_LLM_RESPONSE).build());

        Path imagePath = createTestImage();
        tool.knowledgeAcquireFromFile(imagePath.toString(), null, false, false);
        int callsAfterFirst = mockingDetails(mockLlm).getInvocations().size();

        String forced = tool.knowledgeAcquireFromFile(imagePath.toString(), null, false, true);
        assertFalse(forced.contains("already_known"), "force=true must bypass already_known, got: " + forced);
        assertTrue(mockingDetails(mockLlm).getInvocations().size() > callsAfterFirst,
                "force=true must trigger new LLM calls");
    }

    @Test
    void pendingProposalApprovedViaKnowledgeReview() throws Exception {
        reset(mockLlm);
        when(mockLlm.chat(any(SpiChatRequest.class)))
                .thenReturn(SpiChatResponse.builder().content(MOCK_LLM_RESPONSE).build());
        // 挂起网关：模拟非交互通道
        knowledgeManager.setReviewGate((proposalId, preview) -> ReviewDecision.PENDING);

        String result = tool.knowledgeAcquire("Pending review content", null, false);
        assertTrue(result.contains("pending_review"), "Expected pending_review, got: " + result);
        assertTrue(result.contains("Proposal ID:"), "Expected proposal id in output, got: " + result);

        String proposalId = result.split("Proposal ID: ")[1].split("\n")[0].trim();
        // 挂起时不落盘
        Path pendingFile = tempDir.resolve(".meta-claw/vessels/smoke/knowledge/.pending/" + proposalId + ".json");
        assertTrue(Files.exists(pendingFile), "Pending proposal should be persisted");

        String approved = tool.knowledgeReview(proposalId, "approve");
        assertTrue(approved.contains("Committed"), "Approve should commit, got: " + approved);
        assertFalse(Files.exists(pendingFile), "Pending file should be removed after approval");
    }

    @Test
    void rejectedProposalCommitsNothing() {
        knowledgeManager.setReviewGate((proposalId, preview) -> ReviewDecision.REJECTED);

        String result = tool.knowledgeAcquire("Rejected content", null, false);
        assertTrue(result.contains("rejected"), "Expected rejected status, got: " + result);
        assertFalse(result.contains("Committed"), "Rejected proposal must not commit, got: " + result);
    }

    @Test
    void dryRunSkipsAnalysisAndDefersItToApproval() throws Exception {
        reset(mockLlm);
        when(mockLlm.chat(any(SpiChatRequest.class)))
                .thenReturn(SpiChatResponse.builder().content(MOCK_VISION_RESPONSE).build())
                .thenReturn(SpiChatResponse.builder().content(MOCK_LLM_RESPONSE).build());

        Path imagePath = createTestImage();
        String result = tool.knowledgeAcquireFromFile(imagePath.toString(), null, true, false);

        // dryRun 只做图片识别（一次视觉 LLM 调用），跳过分析/矛盾自检，
        // 但必须返回完整提取内容与可复用的提案 ID
        assertTrue(result.contains("extracted"), "Expected extracted status, got: " + result);
        assertTrue(result.contains("一张测试图片"), "Expected full vision description in result, got: " + result);
        assertTrue(result.contains("Proposal ID:"), "Expected proposal id, got: " + result);
        verify(mockLlm, times(1)).chat(any(SpiChatRequest.class));
        String proposalId = result.split("Proposal ID: ")[1].split("\n")[0].trim();

        // 重复 dryRun 复用同一提案，不重复消耗 LLM
        String second = tool.knowledgeAcquireFromFile(imagePath.toString(), null, true, false);
        assertTrue(second.contains(proposalId), "Repeated dryRun should reuse the same proposal, got: " + second);
        verify(mockLlm, times(1)).chat(any(SpiChatRequest.class));

        // approve 时补跑一次分析（关键词提取 + 统一分析含矛盾自检）后落库，无需重新提取
        String approved = tool.knowledgeReview(proposalId, "approve");
        assertTrue(approved.contains("Committed"), "Approve should commit, got: " + approved);
        assertTrue(mockingDetails(mockLlm).getInvocations().size() > 1,
                "Approving a dryRun proposal should run the deferred analysis (no re-extraction)");
    }

    @Test
    void knowledgeIndexAppearsAfterAcquisition() {
        // 空库时索引为空串（system prompt 区块自动折叠）
        assertEquals("", knowledgeManager.buildKnowledgeIndex("smoke"));

        tool.knowledgeAcquire("Smoke test content", null, false);

        String index = knowledgeManager.buildKnowledgeIndex("smoke");
        assertTrue(index.contains("knowledgeRetrieve"), "Index should contain usage guide, got: " + index);
        assertTrue(index.contains("Smoke Test"), "Index should contain entry title, got: " + index);
        assertTrue(index.contains("smoke"), "Index should contain entry topics, got: " + index);
    }

    private Path createTestImage() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        Path imagePath = tempDir.resolve("test.png");
        Files.write(imagePath, baos.toByteArray());
        return imagePath;
    }
}
