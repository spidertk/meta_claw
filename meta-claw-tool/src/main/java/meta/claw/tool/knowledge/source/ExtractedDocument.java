package meta.claw.tool.knowledge.source;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedDocument {
    private String markdownBody;
    private String mediaType;
    @Builder.Default
    private List<AssetRef> embeddedAssets = Collections.emptyList();
    @Builder.Default
    private Map<String, Object> metadata = Collections.emptyMap();
}
