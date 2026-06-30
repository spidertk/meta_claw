package meta.claw.tool.knowledge.extract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import meta.claw.tool.knowledge.asset.AssetManager;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionContext {
    private AssetManager assetManager;
    private String vesselId;
}
