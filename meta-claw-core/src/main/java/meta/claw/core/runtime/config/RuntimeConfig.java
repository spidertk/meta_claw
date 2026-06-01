package meta.claw.core.runtime.config;

import lombok.Getter;
import lombok.Setter;
import meta.claw.core.infra.config.MemoryConfig;
import meta.claw.core.infra.config.ProviderConfig;
import meta.claw.core.user.VesselMeta;

@Getter
@Setter
public class RuntimeConfig {
    private VesselMeta vesselMeta;
    private ProviderConfig providerConfig;
    private MemoryConfig memoryConfig;
}
