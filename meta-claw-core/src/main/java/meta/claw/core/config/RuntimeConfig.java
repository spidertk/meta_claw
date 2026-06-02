package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;
import meta.claw.core.config.MemoryConfig;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.VesselMeta;

@Getter
@Setter
public class RuntimeConfig {
    private VesselMeta vesselMeta;
    private ProviderConfig providerConfig;
    private MemoryConfig memoryConfig;
}
