package meta.claw.core.knowledge.multimodal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "meta-claw.llm.multimodal")
public class MultimodalConfig {
    private boolean enabled = false;
    private Set<String> supportedMediaTypes = Set.of("image/png", "image/jpeg", "image/webp");
    private boolean pdfPageImages = false;
    private long maxImageSizeBytes = 5 * 1024 * 1024;
}
