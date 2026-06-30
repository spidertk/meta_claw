package meta.claw.tool.knowledge.extract;

import meta.claw.tool.knowledge.source.AssetRef;
import meta.claw.tool.knowledge.source.ExtractedDocument;
import meta.claw.tool.knowledge.source.KnowledgeSource;
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
        AssetRef asset = ctx.getAssetManager().store(source, ctx.getVesselId());
        String description = visionDescriber.describe(asset.getOriginalPath(), source.getMediaType());

        return ExtractedDocument.builder()
                .markdownBody("## 图片描述\n\n" + description + "\n\n![image](assets/" + asset.getAssetId() + "/" + asset.getOriginalPath().getFileName() + ")")
                .mediaType(source.getMediaType())
                .embeddedAssets(List.of(asset))
                .metadata(Map.of("width", 0, "height", 0))
                .build();
    }
}
