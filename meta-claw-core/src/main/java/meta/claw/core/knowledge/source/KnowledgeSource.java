package meta.claw.core.knowledge.source;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSource {
    private String sourceId;
    private String mediaType;       // text/plain, image/png, application/pdf, video/url.douyin
    private URI uri;                // local path or remote URL
    private InputStream stream;     // inline byte stream
    private String originalName;
    private String content;         // shortcut for text/plain
    private Map<String, Object> extra;

    public boolean isText() {
        return "text/plain".equals(mediaType);
    }
}
