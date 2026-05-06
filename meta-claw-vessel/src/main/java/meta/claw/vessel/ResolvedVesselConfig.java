package meta.claw.vessel;

import lombok.Getter;
import lombok.Setter;
import meta.claw.core.model.ProviderConfig;

@Getter
@Setter
public class ResolvedVesselConfig {
    private String providerName;
    private ProviderConfig providerConfig;
    private VesselConfig vesselConfig;
}
