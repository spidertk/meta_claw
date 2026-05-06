package meta.claw.vessel;

import lombok.Getter;
import lombok.Setter;

/**
 * Vessel profile configuration model, mapping {@code ~/.meta-claw/vessels/<name>/config.yaml}.
 */
@Getter
@Setter
public class VesselProfileConfig {
    private String provider;
    private String apiKey;
    private String baseUrl;
    private String model;
    private Double temperature;
    private Double timeout;
}
