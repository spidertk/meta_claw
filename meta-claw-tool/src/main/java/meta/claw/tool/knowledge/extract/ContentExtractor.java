package meta.claw.tool.knowledge.extract;

import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;

public interface ContentExtractor {
    boolean supports(KnowledgeSource source);

    ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx);
}
