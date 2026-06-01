package meta.claw.core.vessel;

import lombok.Getter;
import lombok.Setter;
import meta.claw.core.config.ProviderConfig;
import meta.claw.core.config.VesselConfig;

/**
 * @deprecated Use {@link meta.claw.core.runtime.config.RuntimeConfig} instead.
 */
@Deprecated
@Getter
@Setter
public class ResolvedVesselConfig {
    private ProviderConfig providerConfig;
    private VesselConfig vesselConfig;
}
