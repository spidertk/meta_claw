package meta.claw.core.knowledge.extract;

import meta.claw.core.knowledge.source.AssetRef;
import meta.claw.core.knowledge.source.ExtractedDocument;
import meta.claw.core.knowledge.source.KnowledgeSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 内容提取：有文本层时直接抽文本（零 LLM 调用）；
 * 无文本层（扫描件）时把页图合并为一次视觉调用（最多 5 页），
 * 而不是每页各调一次。
 */
@Component
public class PdfExtractor implements ContentExtractor {

    private static final int MAX_VISION_PAGES = 5;

    private final VisionDescriber visionDescriber;

    @Autowired
    public PdfExtractor(VisionDescriber visionDescriber) {
        this.visionDescriber = visionDescriber;
    }

    @Override
    public boolean supports(KnowledgeSource source) {
        return "application/pdf".equals(source.getMediaType());
    }

    @Override
    public ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx) {
        // 源资产由调用方（KnowledgeManager）统一入库，避免重复存储
        AssetRef pdfAsset = ctx.getSourceAsset() != null
                ? ctx.getSourceAsset()
                : ctx.getAssetManager().store(source, ctx.getVesselId());
        Path pdfPath = pdfAsset.getOriginalPath();

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(document);
            boolean hasText = fullText != null && !fullText.isBlank();

            String title = source.getOriginalName() != null ? source.getOriginalName() : "PDF Document";
            StringBuilder markdown = new StringBuilder();
            markdown.append("# ").append(title).append("\n\n");

            if (hasText) {
                markdown.append("## 提取文本\n\n").append(fullText).append("\n\n");
            }

            List<AssetRef> pageAssets = new ArrayList<>();
            List<String> keywords = new ArrayList<>();

            if (!hasText) {
                // 扫描件：渲染页图，合并为一次视觉调用
                PDFRenderer renderer = new PDFRenderer(document);
                int pageCount = document.getNumberOfPages();
                List<Path> pageImages = new ArrayList<>();
                for (int i = 0; i < Math.min(pageCount, MAX_VISION_PAGES); i++) {
                    Path pageImage = renderPage(pdfPath.getParent(), renderer, i);
                    pageImages.add(pageImage);
                    pageAssets.add(AssetRef.builder()
                            .assetId(pdfAsset.getAssetId())
                            .mediaType("image/png")
                            .originalPath(pageImage)
                            .build());
                }
                VisionInsight insight = visionDescriber.analyze(pageImages, "image/png", ctx.getVesselId());
                markdown.append("## 页面内容（视觉识别，共 ")
                        .append(pageCount).append(" 页，分析前 ")
                        .append(pageImages.size()).append(" 页）\n\n")
                        .append(insight.getDescription()).append("\n\n");
                if (insight.getKeywords() != null) {
                    keywords.addAll(insight.getKeywords());
                }
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("page_count", document.getNumberOfPages());
            metadata.put("has_text", hasText);

            return ExtractedDocument.builder()
                    .markdownBody(markdown.toString())
                    .mediaType("application/pdf")
                    .keywords(keywords)
                    .embeddedAssets(pageAssets)
                    .metadata(metadata)
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Failed to extract PDF: " + pdfPath, e);
        }
    }

    private Path renderPage(Path assetDir, PDFRenderer renderer, int pageIndex) throws IOException {
        BufferedImage image = renderer.renderImageWithDPI(pageIndex, 150);
        Path imagePath = assetDir.resolve("page_" + String.format("%03d", pageIndex + 1) + ".png");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            Files.write(imagePath, baos.toByteArray());
        }
        return imagePath;
    }
}
