package meta.claw.core.config;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.model.VesselConfig;
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
public class VesselConfigLoader {

    private static final String VESSEL_MD = "vessel.md";
    private static final String CONFIG_YAML = "config.yaml";
    private final Yaml yaml = new Yaml();

    public List<VesselConfig> loadFromDirectory(Path dir) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            log.warn("Vessel 配置目录不存在: {}", dir);
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            return paths
                    .filter(Files::isDirectory)
                    .map(this::loadFromVesselDir)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("扫描 Vessel 配置目录失败: {}", dir, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从 Vessel 目录加载完整配置：config.yaml（frontmatter）+ vessel.md（body sections）
     */
    public VesselConfig loadFromVesselDir(Path vesselDir) {
        VesselConfig config = loadConfigYaml(vesselDir.resolve(CONFIG_YAML));
        parseVesselMd(vesselDir.resolve(VESSEL_MD), config);
        return config;
    }

    private VesselConfig loadConfigYaml(Path path) {
        VesselConfig config = new VesselConfig();
        if (!Files.exists(path)) {
            log.warn("Vessel config.yaml 不存在: {}", path);
            return config;
        }
        try (InputStream input = Files.newInputStream(path)) {
            Map<String, Object> map = yaml.load(input);
            if (map == null) {
                log.warn("Vessel config.yaml 解析为空: {}", path);
                return config;
            }
            return mapToConfig(map);
        } catch (IOException e) {
            log.error("加载 Vessel config.yaml 失败: {}", path, e);
            return config;
        }
    }

    private void parseVesselMd(Path path, VesselConfig config) {
        if (!Files.exists(path)) {
            log.warn("Vessel vessel.md 不存在: {}", path);
            return;
        }
        try {
            String content = Files.readString(path);
            String[] lines = content.split("\n");
            String currentSection = null;
            StringBuilder currentContent = new StringBuilder();

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("## ")) {
                    if (currentSection != null) {
                        setSection(config, currentSection, currentContent.toString().trim());
                    }
                    currentSection = trimmed.substring(3).trim().toLowerCase();
                    currentContent = new StringBuilder();
                } else if (currentSection != null) {
                    currentContent.append(line).append("\n");
                }
            }

            if (currentSection != null) {
                setSection(config, currentSection, currentContent.toString().trim());
            }
        } catch (IOException e) {
            log.error("解析 Vessel vessel.md 失败: {}", path, e);
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
