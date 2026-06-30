package meta.claw.tool.knowledge.extract.video;

import meta.claw.tool.knowledge.extract.ExtractionContext;
import meta.claw.tool.knowledge.source.ExtractedDocument;

import java.net.URI;

public interface VideoExtractor {
    boolean supports(URI uri);
    ExtractedDocument extract(URI uri, ExtractionContext ctx) throws Exception;
}
