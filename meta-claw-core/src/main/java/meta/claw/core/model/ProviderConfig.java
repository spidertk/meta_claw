package meta.claw.core.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Provider 配置模型，对应 config.yaml 中 providers.<name> 下的字段。
 */
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
