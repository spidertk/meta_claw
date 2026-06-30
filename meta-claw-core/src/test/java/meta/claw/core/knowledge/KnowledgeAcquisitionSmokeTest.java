package meta.claw.core.knowledge;

import meta.claw.core.llm.SpiChatRequest;
import meta.claw.core.llm.SpiChatResponse;
import meta.claw.core.llm.SpiLlmClient;
import meta.claw.core.runtime.VesselContext;
import meta.claw.core.knowledge.KnowledgeTool;
import meta.claw.core.knowledge.asset.AssetManager;
import meta.claw.core.knowledge.asset.LocalAssetManager;
import meta.claw.core.knowledge.extract.*;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KnowledgeAcquisitionSmokeTest {

    @TempDir
    Path tempDir;

    private KnowledgeTool tool;
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

        SpiLlmClient mockLlm = mock(SpiLlmClient.class);
        when(mockLlm.chat(any(SpiChatRequest.class)))
                .thenReturn(SpiChatResponse.builder().content(MOCK_LLM_RESPONSE).build());

        GitManager gitManager = new GitManager();
        gitManager.init(tempDir.resolve(".meta-claw/vessels/smoke/knowledge"));

        AssetManager assetManager = new LocalAssetManager();
        VisionDescriber visionDescriber = new VisionDescriber(mockLlm);
        ContentExtractorService extractorService = new ContentExtractorService(List.of(
                new TextExtractor(),
                new ImageExtractor(visionDescriber),
                new PdfExtractor(visionDescriber)
        ));
        KnowledgeAnalyzer analyzer = new KnowledgeAnalyzer(mockLlm, new ModelCapability(new MultimodalConfig()));
        KnowledgeManager knowledgeManager = new KnowledgeManager(gitManager, analyzer, extractorService, assetManager);

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
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        Path imagePath = tempDir.resolve("test.png");
        Files.write(imagePath, baos.toByteArray());

        String result = tool.knowledgeAcquireFromFile(imagePath.toString(), null, false);
        assertTrue(result.contains("Committed"), "Expected committed status for image, got: " + result);
    }
}
