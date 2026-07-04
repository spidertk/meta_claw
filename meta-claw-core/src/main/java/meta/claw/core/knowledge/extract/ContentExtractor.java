package meta.claw.core.knowledge.extract;

import meta.claw.core.knowledge.source.ExtractedDocument;
import meta.claw.core.knowledge.source.KnowledgeSource;

public interface ContentExtractor {
    boolean supports(KnowledgeSource source);

    ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx);
}
