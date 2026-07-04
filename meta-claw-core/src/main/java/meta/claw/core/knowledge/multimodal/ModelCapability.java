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
}
