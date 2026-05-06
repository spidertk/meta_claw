package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
public class VesselProfileLoader {

    private static final String CONFIG_FILE = "config.yaml";
    private final Yaml yaml = new Yaml();

    public VesselProfileConfig load(Path vesselDir) {
        Path file = vesselDir.resolve(CONFIG_FILE);
        if (!Files.exists(file)) {
            log.warn("Vessel profile config not found: {}", file);
            return null;
        }
        try (InputStream input = Files.newInputStream(file)) {
            Map<String, Object> map = yaml.load(input);
            if (map == null) {
                log.warn("Vessel profile config is empty: {}", file);
                return null;
            }
            return mapToConfig(map);
        } catch (IOException e) {
            log.error("Failed to load vessel profile config: {}", file, e);
            return null;
        }
    }

    private VesselProfileConfig mapToConfig(Map<String, Object> map) {
        VesselProfileConfig config = new VesselProfileConfig();
        config.setProvider(getString(map, "provider"));
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
}
