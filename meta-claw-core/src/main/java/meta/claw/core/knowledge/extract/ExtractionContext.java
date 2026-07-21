package meta.claw.core.knowledge.extract;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import meta.claw.core.knowledge.asset.AssetManager;
import meta.claw.core.knowledge.source.AssetRef;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionContext {
    private AssetManager assetManager;
    private String vesselId;
    /** 调用方已入库的源资产；非空时提取器不得再次 store，避免重复资产。 */
    private AssetRef sourceAsset;
}
