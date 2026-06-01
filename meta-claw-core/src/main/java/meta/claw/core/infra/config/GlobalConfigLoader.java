package meta.claw.core.infra.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component
public class GlobalConfigLoader {

    private static final String CONFIG_FILE = "config.yaml";
    private final Yaml yaml = SnakeYamlFactory.createCamelCaseYaml();

    public GlobalConfig load(Path baseDir) {
        Path file = baseDir.resolve(CONFIG_FILE);
        if (!Files.exists(file)) {
            log.warn("Global config not found: {}", file);
            return null;
        }
        try (InputStream input = Files.newInputStream(file)) {
            return yaml.loadAs(input, GlobalConfig.class);
        } catch (IOException e) {
            log.error("Failed to load global config: {}", file, e);
            return null;
        }
    }
}
