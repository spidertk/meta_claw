package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.VesselConfig;
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
//                    .filter(config -> config != null && config.getId() != null)
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
            VesselConfig config = mapToConfig(map);
            // 解析 Markdown body section
            parseMarkdownSections(config, content);
            return config;
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

    private void parseMarkdownSections(VesselConfig config, String content) {
        int secondDelimiter = content.indexOf("---", content.indexOf("---") + 3);
        if (secondDelimiter == -1) return;

        String body = content.substring(secondDelimiter + 3).trim();
        if (body.isBlank()) return;

        String[] lines = body.split("\n");
        String currentSection = null;
        StringBuilder currentContent = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                // 保存上一个 section
                if (currentSection != null) {
                    setSection(config, currentSection, currentContent.toString().trim());
                }
                currentSection = trimmed.substring(3).trim().toLowerCase();
                currentContent = new StringBuilder();
            } else if (currentSection != null) {
                currentContent.append(line).append("\n");
            }
        }

        // 保存最后一个 section
        if (currentSection != null) {
            setSection(config, currentSection, currentContent.toString().trim());
        }
    }

    private void setSection(VesselConfig config, String sectionName, String sectionContent) {
        switch (sectionName) {
            case "identity" -> config.setIdentity(sectionContent);
            case "soul" -> config.setSoul(sectionContent);
            case "domain knowledge" -> config.setDomainKnowledge(sectionContent);
            case "capabilities" -> config.setCapabilities(sectionContent);
            case "guidelines" -> config.setGuidelines(sectionContent);
            case "preferences" -> config.setPreferences(sectionContent);
            default -> log.debug("未知 Vessel section: {}", sectionName);
        }
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
        Object preferences = map.get("preferences_enabled");
        config.setPreferencesEnabled(preferences instanceof Boolean ? (Boolean) preferences : false);
        config.setKnowledgeDir(getString(map, "knowledge_dir"));
        config.setRole(getString(map, "role"));
        Object autoServe = map.get("auto_serve");
        config.setAutoServe(autoServe instanceof Boolean ? (Boolean) autoServe : false);
        Object excludeTools = map.get("exclude_tools");
        if (excludeTools instanceof List) {
            config.setExcludeTools(((List<?>) excludeTools).stream().map(Object::toString).collect(Collectors.toList()));
        }
        return config;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
