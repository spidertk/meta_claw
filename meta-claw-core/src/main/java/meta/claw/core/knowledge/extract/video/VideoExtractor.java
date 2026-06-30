package meta.claw.core.knowledge.extract.video;

import meta.claw.core.knowledge.extract.ExtractionContext;
import meta.claw.core.knowledge.source.ExtractedDocument;

import java.net.URI;

public interface VideoExtractor {
    boolean supports(URI uri);
    ExtractedDocument extract(URI uri, ExtractionContext ctx) throws Exception;
}
