package meta.claw.tool.knowledge.extract;

import meta.claw.core.knowledge.asset.LocalAssetManager;
import meta.claw.core.knowledge.extract.ExtractionContext;
import meta.claw.core.knowledge.extract.PdfExtractor;
import meta.claw.core.knowledge.extract.VisionDescriber;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PdfExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsTextFromPdf() throws Exception {
        System.setProperty("user.dir", tempDir.toString());
        try {
            byte[] pdfBytes = createSimplePdf("Hello PDF");

            VisionDescriber describer = mock(VisionDescriber.class);
            when(describer.describe(any(Path.class), anyString())).thenReturn("A page image");

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
            assertTrue(doc.getMarkdownBody().contains("A page image"));
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
}
