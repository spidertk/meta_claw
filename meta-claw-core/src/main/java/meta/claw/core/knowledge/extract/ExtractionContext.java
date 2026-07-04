package meta.claw.core.knowledge.extract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import meta.claw.core.knowledge.asset.AssetManager;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionContext {
    private AssetManager assetManager;
    private String vesselId;
}
