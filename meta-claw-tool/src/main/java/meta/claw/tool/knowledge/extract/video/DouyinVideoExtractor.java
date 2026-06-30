package meta.claw.tool.knowledge.extract.video;

import meta.claw.tool.knowledge.extract.ContentExtractor;
import meta.claw.tool.knowledge.extract.ExtractionContext;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

@Component
public class DouyinVideoExtractor implements ContentExtractor {

    private final List<VideoExtractor> videoExtractors;

    @Autowired
    public DouyinVideoExtractor(List<VideoExtractor> videoExtractors) {
        this.videoExtractors = videoExtractors;
    }

    @Override
    public boolean supports(KnowledgeSource source) {
        return "video/url.douyin".equals(source.getMediaType());
    }

    @Override
    public ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx) {
        URI uri = source.getUri();
        if (uri == null) {
            throw new IllegalArgumentException("Douyin video source requires a URI");
        }
        for (VideoExtractor extractor : videoExtractors) {
            if (extractor.supports(uri)) {
                try {
                    return extractor.extract(uri, ctx);
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to extract video: " + uri, e);
                }
            }
        }
        throw new UnsupportedOperationException("No video extractor supports URI: " + uri);
    }
}
