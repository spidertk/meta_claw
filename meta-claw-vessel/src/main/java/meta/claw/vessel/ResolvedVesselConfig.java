package meta.claw.vessel;

import lombok.Getter;
import lombok.Setter;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.VesselConfig;

@Getter
@Setter
public class ResolvedVesselConfig {
    private String providerName;
    private ProviderConfig providerConfig;
    private VesselConfig vesselConfig;
}
