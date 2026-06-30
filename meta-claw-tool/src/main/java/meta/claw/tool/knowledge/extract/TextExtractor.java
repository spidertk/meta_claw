package meta.claw.tool.knowledge.extract;

import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
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
