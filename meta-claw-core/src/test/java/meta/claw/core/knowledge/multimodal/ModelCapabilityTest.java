package meta.claw.core.knowledge.multimodal;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelCapabilityTest {

    @Test
    void whenEnabledSupportsConfiguredTypes() {
        MultimodalConfig config = new MultimodalConfig();
        config.setEnabled(true);
        config.setSupportedMediaTypes(Set.of("image/png"));

        ModelCapability capability = new ModelCapability(config);
        assertTrue(capability.supportsMultimodal());
        assertTrue(capability.supportsMediaType("image/png"));
        assertFalse(capability.supportsMediaType("image/webp"));
    }

    @Test
    void whenDisabledRejectsAll() {
        MultimodalConfig config = new MultimodalConfig();
        ModelCapability capability = new ModelCapability(config);
        assertFalse(capability.supportsMultimodal());
        assertFalse(capability.supportsMediaType("image/png"));
    }
}
