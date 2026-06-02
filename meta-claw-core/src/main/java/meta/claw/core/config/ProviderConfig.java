package meta.claw.core.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProviderConfig {
    private String provider;
    private String apiKey;
    private String baseUrl;
    private String model;
    private Double temperature;
    private Double timeout;
}
