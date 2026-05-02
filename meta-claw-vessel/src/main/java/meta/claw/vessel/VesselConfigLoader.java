package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class VesselConfigLoader {

    private static final String CONFIG_FILE = "vessel.md";
    private final Yaml yaml = new Yaml();

    public List<VesselConfig> loadFromDirectory(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("Vessel 配置目录不存在: {}", dir);
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
            log.error("扫描 Vessel 配置目录失败: {}", dir, e);
            return Collections.emptyList();
        }
    }

    public VesselConfig loadSingle(Path path) {
        try {
            String content = Files.readString(path);
            String yamlContent = extractYamlFrontmatter(content);
            if (yamlContent == null || yamlContent.isBlank()) {
                log.warn("vessel.md 中没有 YAML frontmatter: {}", path);
                return null;
            }
            Map<String, Object> map = yaml.load(yamlContent);
            if (map == null) {
                log.warn("YAML frontmatter 解析为空: {}", path);
                return null;
            }
            return mapToConfig(map);
        } catch (IOException e) {
            log.error("加载 Vessel 配置失败: {}", path, e);
            return null;
        }
    }

    private String extractYamlFrontmatter(String content) {
        int first = content.indexOf("---");
        if (first == -1) return null;
        int second = content.indexOf("---", first + 3);
        if (second == -1) return null;
        return content.substring(first + 3, second).trim();
    }

    @SuppressWarnings("unchecked")
    private VesselConfig mapToConfig(Map<String, Object> map) {
        VesselConfig config = new VesselConfig();
        config.setId(getString(map, "id"));
        config.setName(getString(map, "name"));
        config.setDescription(getString(map, "description"));
        config.setEmoji(getString(map, "emoji"));
        config.setModel(getString(map, "model"));
        config.setSystemPrompt(getString(map, "system_prompt"));
        Object memory = map.get("memory_enabled");
        config.setMemoryEnabled(memory instanceof Boolean ? (Boolean) memory : false);
        config.setKnowledgeDir(getString(map, "knowledge_dir"));
        return config;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
