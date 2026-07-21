package meta.claw.core.knowledge.model;

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
public class AnalysisResult {
    @Builder.Default
    private String knowledgeType = "unknown";
    @Builder.Default
    private boolean isFact = false;
    @Builder.Default
    private ContradictionInfo contradiction = ContradictionInfo.builder().detected(false).build();
    @Builder.Default
    private double confidence = 0.0;
    @Builder.Default
    private String recommendedAction = "manual_review";
    @Builder.Default
    private String reasoning = "";
    @Builder.Default
    private List<String> extractedKeywords = Collections.emptyList();
    @Builder.Default
    private List<String> suggestedTopics = Collections.emptyList();
    @Builder.Default
    private String suggestedTitle = "";
    @Builder.Default
    private String commitSummary = "";
    @Builder.Default
    private String commitDescription = "";
    @Builder.Default
    private Map<String, Object> rawResponse = Collections.emptyMap();
    @Builder.Default
    private boolean multimodalUsed = false;

    public boolean shouldAutoExecute(double threshold) {
        return confidence >= threshold
                && !contradiction.isDetected()
                && ("add".equals(recommendedAction) || "replace".equals(recommendedAction));
    }

    public boolean needsManualConfirmation(double threshold) {
        return confidence < threshold
                || contradiction.isDetected()
                || "manual_review".equals(recommendedAction);
    }
}