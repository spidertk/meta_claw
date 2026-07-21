package meta.claw.core.knowledge.source;

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
    /** 提取阶段（如视觉理解）已产出的关键词；非空时无需再单独调用 LLM 提取。 */
    @Builder.Default
    private List<String> keywords = Collections.emptyList();
    @Builder.Default
    private List<AssetRef> embeddedAssets = Collections.emptyList();
    @Builder.Default
    private Map<String, Object> metadata = Collections.emptyMap();
}
