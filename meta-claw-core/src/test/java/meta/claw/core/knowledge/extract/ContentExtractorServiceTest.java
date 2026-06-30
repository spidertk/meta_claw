package meta.claw.core.knowledge.extract;

import meta.claw.core.knowledge.source.ExtractedDocument;
import meta.claw.core.knowledge.source.KnowledgeSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentExtractorServiceTest {

    @Test
    void routesTextSourceToTextExtractor() {
        ContentExtractorService service = new ContentExtractorService(List.of(new TextExtractor()));
        KnowledgeSource source = KnowledgeSource.builder()
                .mediaType("text/plain")
                .content("hello world")
                .build();

        ExtractedDocument doc = service.extract(source, ExtractionContext.builder().build());
        assertEquals("hello world", doc.getMarkdownBody());
        assertEquals("text/plain", doc.getMediaType());
    }

    @Test
    void throwsWhenNoExtractorSupports() {
        ContentExtractorService service = new ContentExtractorService(List.of(new TextExtractor()));
        KnowledgeSource source = KnowledgeSource.builder().mediaType("image/png").build();
        assertThrows(UnsupportedOperationException.class,
                () -> service.extract(source, ExtractionContext.builder().build()));
    }
}
