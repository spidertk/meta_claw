package meta.claw.core.knowledge.extract;

import meta.claw.core.knowledge.source.AssetRef;
import meta.claw.core.knowledge.source.ExtractedDocument;
import meta.claw.core.knowledge.source.KnowledgeSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class ImageExtractor implements ContentExtractor {

    private final VisionDescriber visionDescriber;

    @Autowired
    public ImageExtractor(VisionDescriber visionDescriber) {
        this.visionDescriber = visionDescriber;
    }

    @Override
    public boolean supports(KnowledgeSource source) {
        return source.getMediaType() != null && source.getMediaType().startsWith("image/");
    }

    @Override
    public ExtractedDocument extract(KnowledgeSource source, ExtractionContext ctx) {
        // 源资产由调用方（KnowledgeManager）统一入库，避免重复存储
        AssetRef asset = ctx.getSourceAsset() != null
                ? ctx.getSourceAsset()
                : ctx.getAssetManager().store(source, ctx.getVesselId());

        VisionInsight insight = visionDescriber.analyze(asset.getOriginalPath(), source.getMediaType(), ctx.getVesselId());

        return ExtractedDocument.builder()
                .markdownBody("## 图片描述\n\n" + insight.getDescription() + "\n\n![image](assets/" + asset.getAssetId() + "/" + asset.getOriginalPath().getFileName() + ")")
                .mediaType(source.getMediaType())
                .keywords(insight.getKeywords())
                .embeddedAssets(List.of(asset))
                .metadata(Map.of("width", 0, "height", 0))
                .build();
    }
}
