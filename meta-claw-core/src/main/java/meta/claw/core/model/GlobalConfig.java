package meta.claw.core.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 全局配置模型，映射 ~/.meta-claw/config.yaml。
 */
@Getter
@Setter
public class GlobalConfig {
    private String defaultProvider;
    private Map<String, ProviderConfig> providers;
}
