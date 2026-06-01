package meta.claw.core.infra.config;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class GlobalConfig {
    private String defaultProvider;
    private Map<String, ProviderConfig> providers;
    private Boolean logDebug;
}
