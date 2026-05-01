package meta.claw.export;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.ExpertConfig;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class ExpertConfigLoader {

    private static final String CONFIG_FILE = "expert.yaml";
    private final Yaml yaml = new Yaml();

    public List<ExpertConfig> loadFromDirectory(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("专家配置目录不存在: {}", dir);
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(sub -> sub.resolve(CONFIG_FILE))
                    .filter(Files::exists)
                    .map(this::loadSingle)
                    .filter(config -> config != null && config.getId() != null)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("扫描专家配置目录失败: {}", dir, e);
            return Collections.emptyList();
        }
    }

    public ExpertConfig loadSingle(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            Map<String, Object> map = yaml.load(input);
            if (map == null) {
                log.warn("配置文件为空: {}", path);
                return null;
            }
            return mapToConfig(map);
        } catch (IOException e) {
            log.error("加载配置文件失败: {}", path, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private ExpertConfig mapToConfig(Map<String, Object> map) {
        ExpertConfig config = new ExpertConfig();
        config.setId(getString(map, "id"));
        config.setName(getString(map, "name"));
        config.setDescription(getString(map, "description"));
        config.setEmoji(getString(map, "emoji"));
        config.setModel(getString(map, "model"));
        config.setSystemPrompt(getString(map, "systemPrompt"));
        config.setMemoryEnabled(getBoolean(map, "memoryEnabled"));
        config.setKnowledgeDir(getString(map, "knowledgeDir"));
        return config;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private boolean getBoolean(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof Boolean ? (Boolean) value : false;
    }
}
