package meta.claw.tool.knowledge.extract;

import meta.claw.core.knowledge.asset.LocalAssetManager;
import meta.claw.core.knowledge.extract.ExtractionContext;
import meta.claw.core.knowledge.extract.PdfExtractor;
import meta.claw.core.knowledge.extract.VisionDescriber;
import meta.claw.core.knowledge.extract.VisionInsight;
import meta.claw.core.knowledge.source.ExtractedDocument;
import meta.claw.core.knowledge.source.KnowledgeSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PdfExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void textLayerPdfSkipsVisionEntirely() throws Exception {
        System.setProperty("user.dir", tempDir.toString());
        try {
            byte[] pdfBytes = createSimplePdf("Hello PDF");

            VisionDescriber describer = mock(VisionDescriber.class);
            LocalAssetManager assetManager = new LocalAssetManager();
            PdfExtractor extractor = new PdfExtractor(describer);

            KnowledgeSource source = KnowledgeSource.builder()
                    .mediaType("application/pdf")
                    .stream(new ByteArrayInputStream(pdfBytes))
                    .originalName("test.pdf")
                    .build();

            ExtractedDocument doc = extractor.extract(source,
                    ExtractionContext.builder()
                            .assetManager(assetManager)
                            .vesselId("v1")
                            .build());

            assertTrue(doc.getMarkdownBody().contains("Hello PDF"));
            // 有文本层时零视觉调用
            verifyNoInteractions(describer);
        } finally {
            System.clearProperty("user.dir");
        }
    }

    @Test
    void scannedPdfUsesSingleBatchedVisionCall() throws Exception {
        System.setProperty("user.dir", tempDir.toString());
        try {
            // 3 页无文本扫描件
            byte[] pdfBytes = createBlankPdf(3);

            VisionDescriber describer = mock(VisionDescriber.class);
            when(describer.analyze(anyList(), anyString(), anyString()))
                    .thenReturn(VisionInsight.builder()
                            .description("扫描件页面内容")
                            .keywords(List.of("scan", "page"))
                            .build());

            LocalAssetManager assetManager = new LocalAssetManager();
            PdfExtractor extractor = new PdfExtractor(describer);

            KnowledgeSource source = KnowledgeSource.builder()
                    .mediaType("application/pdf")
                    .stream(new ByteArrayInputStream(pdfBytes))
                    .originalName("scan.pdf")
                    .build();

            ExtractedDocument doc = extractor.extract(source,
                    ExtractionContext.builder()
                            .assetManager(assetManager)
                            .vesselId("v1")
                            .build());

            assertTrue(doc.getMarkdownBody().contains("扫描件页面内容"));
            assertEquals(List.of("scan", "page"), doc.getKeywords());
            // 多页合并为一次视觉调用，而不是每页一次
            verify(describer, times(1)).analyze(anyList(), anyString(), anyString());
        } finally {
            System.clearProperty("user.dir");
        }
    }

    private byte[] createSimplePdf(String text) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createBlankPdf(int pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                doc.addPage(new PDPage(PDRectangle.A4));
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
