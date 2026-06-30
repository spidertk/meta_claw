package meta.claw.tool.knowledge.extract;

import lombok.extern.slf4j.Slf4j;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ContentExtractorService {

    private final List<ContentExtractor> extractors;

    @Autowired
    public ContentExtractorService(List<ContentExtractor> extractors) {
        this.extractors = extractors != null ? extractors : List.of();
    }

    public ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx) {
        for (ContentExtractor extractor : extractors) {
            if (extractor.supports(source)) {
                log.debug("Using extractor {} for {}", extractor.getClass().getSimpleName(), source.getMediaType());
                return extractor.extract(source, ctx);
            }
        }
        throw new UnsupportedOperationException("No extractor supports mediaType: " + source.getMediaType());
    }
}
