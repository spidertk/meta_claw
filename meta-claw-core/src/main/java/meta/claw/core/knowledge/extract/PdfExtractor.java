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

@Component
public class PdfExtractor implements ContentExtractor {

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
        AssetRef pdfAsset = ctx.getAssetManager().store(source, ctx.getVesselId());
        Path pdfPath = pdfAsset.getOriginalPath();

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(document);

            String title = source.getOriginalName() != null ? source.getOriginalName() : "PDF Document";
            StringBuilder markdown = new StringBuilder();
            markdown.append("# ").append(title).append("\n\n");

            if (fullText != null && !fullText.isBlank()) {
                markdown.append("## 提取文本\n\n").append(fullText).append("\n\n");
            }

            List<AssetRef> pageAssets = new ArrayList<>();
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                Path pageImage = renderPage(pdfPath.getParent(), renderer, i);
                String pageDescription = visionDescriber.describe(pageImage, "image/png", ctx.getVesselId());
                markdown.append("## 第 ").append(i + 1).append(" 页\n\n")
                        .append(pageDescription).append("\n\n");

                AssetRef pageAsset = AssetRef.builder()
                        .assetId(pdfAsset.getAssetId())
                        .mediaType("image/png")
                        .originalPath(pageImage)
                        .build();
                pageAssets.add(pageAsset);
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("page_count", pageCount);
            metadata.put("has_text", fullText != null && !fullText.isBlank());

            return ExtractedDocument.builder()
                    .markdownBody(markdown.toString())
                    .mediaType("application/pdf")
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
