package meta.claw.core.knowledge.extract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 视觉理解结果：一次 LLM 调用同时产出描述与检索关键词。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisionInsight {
    private String description;
    @Builder.Default
    private List<String> keywords = Collections.emptyList();
}
