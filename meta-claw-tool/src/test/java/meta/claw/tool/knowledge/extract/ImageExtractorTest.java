package meta.claw.tool.knowledge.extract;

import meta.claw.core.knowledge.asset.LocalAssetManager;
import meta.claw.core.knowledge.extract.ExtractionContext;
import meta.claw.core.knowledge.extract.ImageExtractor;
import meta.claw.core.knowledge.extract.VisionDescriber;
import meta.claw.core.knowledge.extract.VisionInsight;
import meta.claw.core.knowledge.source.ExtractedDocument;
import meta.claw.core.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImageExtractorTest {

    @TempDir
    Path tempDir;

    private String originalUserDir;

    @BeforeEach
    void setUp() {
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void extractsImageDescription() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        byte[] bytes = baos.toByteArray();

        VisionDescriber describer = mock(VisionDescriber.class);
        when(describer.analyze(any(Path.class), anyString(), anyString()))
                .thenReturn(VisionInsight.builder()
                        .description("A red square")
                        .keywords(java.util.List.of("red", "square"))
                        .build());

        LocalAssetManager assetManager = new LocalAssetManager();
        ImageExtractor extractor = new ImageExtractor(describer);

        KnowledgeSource source = KnowledgeSource.builder()
                .mediaType("image/png")
                .stream(new ByteArrayInputStream(bytes))
                .originalName("test.png")
                .build();

        ExtractedDocument doc = extractor.extract(source,
                ExtractionContext.builder()
                        .assetManager(assetManager)
                        .vesselId("v1")
                        .build());

        assertTrue(doc.getMarkdownBody().contains("A red square"));
        assertTrue(doc.getEmbeddedAssets().get(0).getOriginalPath().toString().endsWith(".png"));
        // 视觉理解一次调用同时产出关键词，无需第二次 LLM 调用
        assertTrue(doc.getKeywords().contains("red"));
    }
}
