package meta.claw.core.knowledge.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ContradictionInfo {
    private boolean detected;
    @Builder.Default
    private String conflictingEntryId = "";
    @Builder.Default
    private String explanation = "";
    @Builder.Default
    private double confidence = 0.0;
    @Builder.Default
    private String contradictionType = "";
}