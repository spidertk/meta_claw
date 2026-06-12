package meta.claw.core.runtime.skill;

import lombok.extern.slf4j.Slf4j;
import meta.claw.core.infra.ProjectRootFinder;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Skill 注册表。
 * <p>加载系统级 skills 与当前 Vessel 私有 skills，私有 skill 可覆盖系统级同名 skill。</p>
 */
@Slf4j
@Component
public class SkillRegistry {

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();

    /**
     * 加载指定 Vessel 的技能列表。
     */
    public void load(String vesselId) {
        skills.clear();

        Path baseDir = ProjectRootFinder.getMetaClawDir();
        Path systemSkillsDir = baseDir.resolve("skills");
        Path vesselSkillsDir = baseDir.resolve("vessels").resolve(vesselId).resolve("skills");

        // 先加载系统级技能
        loadFromDirectory(systemSkillsDir, false);
        // 再加载 Vessel 私有技能（覆盖同名系统级技能）
        loadFromDirectory(vesselSkillsDir, true);

        log.debug("Loaded {} skills for vessel {}", skills.size(), vesselId);
    }

    private void loadFromDirectory(Path skillsDir, boolean vesselPrivate) {
        if (!Files.exists(skillsDir) || !Files.isDirectory(skillsDir)) {
            return;
        }
        try (Stream<Path> entries = Files.list(skillsDir)) {
            entries.filter(Files::isDirectory)
                    .forEach(skillDir -> loadSkill(skillDir, vesselPrivate));
        } catch (IOException e) {
            log.warn("Failed to list skills directory: {}", skillsDir, e);
        }
    }

    private void loadSkill(Path skillDir, boolean vesselPrivate) {
        Path skillFile = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillFile)) {
            return;
        }
        try {
            String raw = Files.readString(skillFile, StandardCharsets.UTF_8);
            ParsedSkill parsed = parseSkillMarkdown(raw);
            String name = parsed.name != null && !parsed.name.isBlank()
                    ? parsed.name
                    : skillDir.getFileName().toString();
            Skill skill = Skill.builder()
                    .name(name)
                    .description(parsed.description != null ? parsed.description : "")
                    .content(parsed.content)
                    .location(skillFile)
                    .vesselPrivate(vesselPrivate)
                    .build();
            skills.put(skill.getName(), skill);
        } catch (IOException e) {
            log.warn("Failed to load skill file: {}", skillFile, e);
        }
    }

    private ParsedSkill parseSkillMarkdown(String raw) {
        String delimiter = "---";
        if (raw.startsWith(delimiter)) {
            int end = raw.indexOf(delimiter, delimiter.length());
            if (end > 0) {
                String frontMatter = raw.substring(delimiter.length(), end).trim();
                String content = raw.substring(end + delimiter.length()).trim();
                Map<String, Object> meta = parseYaml(frontMatter);
                return new ParsedSkill(
                        (String) meta.get("name"),
                        (String) meta.get("description"),
                        content
                );
            }
        }
        return new ParsedSkill(null, null, raw.trim());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml(String yamlText) {
        if (yamlText == null || yamlText.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(yamlText);
            if (loaded instanceof Map) {
                return (Map<String, Object>) loaded;
            }
        } catch (Exception e) {
            log.warn("Failed to parse skill front matter: {}", yamlText, e);
        }
        return Collections.emptyMap();
    }

    public Optional<Skill> findByName(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    public List<Skill> getAvailableSkills() {
        return Collections.unmodifiableList(new ArrayList<>(skills.values()));
    }

    public List<Skill> getAvailableSkills(String vesselId) {
        load(vesselId);
        return getAvailableSkills();
    }

    private record ParsedSkill(String name, String description, String content) {
    }
}
