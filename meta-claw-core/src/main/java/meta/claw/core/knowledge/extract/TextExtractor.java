package meta.claw.core.knowledge.extract;

import meta.claw.core.knowledge.source.ExtractedDocument;
import meta.claw.core.knowledge.source.KnowledgeSource;
import org.springframework.stereotype.Component;

@Component
public class TextExtractor implements ContentExtractor {

    @Override
    public boolean supports(KnowledgeSource source) {
        return "text/plain".equals(source.getMediaType());
    }

    @Override
    public ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx) {
        return ExtractedDocument.builder()
                .markdownBody(source.getContent() != null ? source.getContent() : "")
                .mediaType("text/plain")
                .build();
    }
}
