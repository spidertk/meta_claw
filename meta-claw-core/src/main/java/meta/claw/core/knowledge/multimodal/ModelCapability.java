package meta.claw.core.knowledge.multimodal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ModelCapability {

    private final MultimodalConfig config;

    @Autowired
    public ModelCapability(MultimodalConfig config) {
        this.config = config;
    }

    public boolean supportsMultimodal() {
        return config.isEnabled();
    }

    public boolean supportsMediaType(String mediaType) {
        return config.isEnabled() && config.getSupportedMediaTypes().contains(mediaType);
    }

    public boolean supportsPdfPageImages() {
        return config.isEnabled() && config.isPdfPageImages();
    }

    /**
     * 知识分析是否允许附带原图。默认关闭：分析走视觉理解产出的文本描述，
     * 只有显式开启 attachImageToAnalysis 且模型声明多模态时才带图。
     */
    public boolean attachImageToAnalysis() {
        return config.isEnabled() && config.isAttachImageToAnalysis();
    }
}
