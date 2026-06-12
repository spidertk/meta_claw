package meta.claw.core.runtime.skill;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SkillRegistryTest {

    private Path tempDir;
    private String originalUserDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("skill-registry-test");
        Files.createDirectories(tempDir.resolve(".meta-claw/skills"));
        Files.createDirectories(tempDir.resolve(".meta-claw/vessels/v1/skills"));
        originalUserDir = System.getProperty("user.dir");
        System.setProperty("user.dir", tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        System.setProperty("user.dir", originalUserDir);
    }

    @Test
    void loadsSystemSkill() throws IOException {
        createSkill(tempDir.resolve(".meta-claw/skills/travel-planner/SKILL.md"),
                "travel-planner", "Plan trips", "# Travel Planner\nAsk budget.");

        SkillRegistry registry = new SkillRegistry();
        registry.load("v1");

        List<Skill> skills = registry.getAvailableSkills();
        assertEquals(1, skills.size());
        assertEquals("travel-planner", skills.get(0).getName());
        assertEquals("Plan trips", skills.get(0).getDescription());
        assertFalse(skills.get(0).isVesselPrivate());
    }

    @Test
    void vesselSkillOverridesSystemSkill() throws IOException {
        createSkill(tempDir.resolve(".meta-claw/skills/code-reviewer/SKILL.md"),
                "code-reviewer", "System reviewer", "# System");
        createSkill(tempDir.resolve(".meta-claw/vessels/v1/skills/code-reviewer/SKILL.md"),
                "code-reviewer", "Vessel reviewer", "# Vessel");

        SkillRegistry registry = new SkillRegistry();
        registry.load("v1");

        Skill skill = registry.findByName("code-reviewer").orElseThrow();
        assertEquals("Vessel reviewer", skill.getDescription());
        assertEquals("# Vessel", skill.getContent());
        assertTrue(skill.isVesselPrivate());
    }

    @Test
    void fallsBackToDirectoryNameWhenNameMissing() throws IOException {
        Path skillFile = tempDir.resolve(".meta-claw/skills/unnamed/SKILL.md");
        Files.createDirectories(skillFile.getParent());
        Files.writeString(skillFile, "No front matter here.", StandardCharsets.UTF_8);

        SkillRegistry registry = new SkillRegistry();
        registry.load("v1");

        Skill skill = registry.findByName("unnamed").orElseThrow();
        assertEquals("unnamed", skill.getName());
        assertEquals("", skill.getDescription());
    }

    @Test
    void emptySkillsDirReturnsEmptyList() {
        SkillRegistry registry = new SkillRegistry();
        registry.load("v1");
        assertTrue(registry.getAvailableSkills().isEmpty());
    }

    private void createSkill(Path path, String name, String description, String content) throws IOException {
        Files.createDirectories(path.getParent());
        String markdown = "---\n" +
                "name: " + name + "\n" +
                "description: " + description + "\n" +
                "---\n\n" +
                content;
        Files.writeString(path, markdown, StandardCharsets.UTF_8);
    }
}
