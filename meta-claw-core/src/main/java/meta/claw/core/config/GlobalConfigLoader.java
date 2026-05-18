package meta.claw.core.config;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.config.GlobalConfig;
import meta.claw.core.config.ProviderConfig;
import org.yaml.snakeyaml.Yaml;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
@Component
public class GlobalConfigLoader {

    private static final String CONFIG_FILE = "config.yaml";
    private final Yaml yaml = new Yaml();

    public GlobalConfig load(Path baseDir) {
        Path file = baseDir.resolve(CONFIG_FILE);
        if (!Files.exists(file)) {
            log.warn("全局配置文件不存在: {}", file);
            return null;
        }
        try (InputStream input = Files.newInputStream(file)) {
            Map<String, Object> map = yaml.load(input);
            if (map == null) {
                log.warn("配置文件为空: {}", file);
                return null;
            }
            return mapToConfig(map);
        } catch (IOException e) {
            log.error("加载全局配置失败: {}", file, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private GlobalConfig mapToConfig(Map<String, Object> map) {
        GlobalConfig config = new GlobalConfig();
        config.setDefaultProvider(getString(map, "default_provider"));
        Object providersObj = map.get("providers");
        if (providersObj instanceof Map) {
            Map<String, Object> providersMap = (Map<String, Object>) providersObj;
            Map<String, ProviderConfig> providers = new java.util.HashMap<>();
            for (Map.Entry<String, Object> entry : providersMap.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    providers.put(entry.getKey(), mapToProviderConfig((Map<String, Object>) entry.getValue()));
                }
            }
            config.setProviders(providers);
        }
        config.setLogDebug(getBoolean(map, "log.debug"));
        return config;
    }

    private ProviderConfig mapToProviderConfig(Map<String, Object> map) {
        ProviderConfig config = new ProviderConfig();
        config.setApiKey(getString(map, "api_key"));
        config.setBaseUrl(getString(map, "base_url"));
        config.setModel(getString(map, "model"));
        Object temp = map.get("temperature");
        if (temp instanceof Number) {
            config.setTemperature(((Number) temp).doubleValue());
        }
        Object timeout = map.get("timeout");
        if (timeout instanceof Number) {
            config.setTimeout(((Number) timeout).doubleValue());
        }
        return config;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return null;
    }
}
