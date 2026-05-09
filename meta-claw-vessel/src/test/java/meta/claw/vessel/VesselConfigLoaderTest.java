package meta.claw.vessel;

import meta.claw.core.model.VesselConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VesselConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testLoadFromVesselDir() throws Exception {
        Path vesselDir = tempDir.resolve("default");
        Files.createDirectories(vesselDir);

        // config.yaml 包含 frontmatter 字段
        String configYaml = """
                id: test-vessel
                name: Test Vessel
                model: kimi-k2.5
                system_prompt: You are a test assistant.
                preferences_enabled: true
                role: member
                auto_serve: false
                """;
        Files.writeString(vesselDir.resolve("config.yaml"), configYaml);

        // vessel.md 包含 Markdown body sections
        String vesselMd = """
                # Test Vessel

                ## Identity

                Test identity

                ## Soul

                Test soul
                """;
        Files.writeString(vesselDir.resolve("vessel.md"), vesselMd);

        VesselConfigLoader loader = new VesselConfigLoader();
        VesselConfig config = loader.loadFromVesselDir(vesselDir);

        assertNotNull(config);
        assertEquals("test-vessel", config.getId());
        assertEquals("Test Vessel", config.getName());
        assertEquals("kimi-k2.5", config.getModel());
        assertTrue(config.isPreferencesEnabled());
        assertEquals("member", config.getRole());
        assertEquals("Test identity", config.getIdentity());
        assertEquals("Test soul", config.getSoul());
    }

    @Test
    void testLoadFromDirectory() throws Exception {
        Path vesselDir = tempDir.resolve("default");
        Files.createDirectories(vesselDir);

        Files.writeString(vesselDir.resolve("config.yaml"), """
                id: default
                name: Default
                model: moonshot
                """);
        Files.writeString(vesselDir.resolve("vessel.md"), """
                # Default

                ## Identity

                Default vessel
                """);

        VesselConfigLoader loader = new VesselConfigLoader();
        List<VesselConfig> configs = loader.loadFromDirectory(tempDir);

        assertEquals(1, configs.size());
        assertEquals("default", configs.get(0).getId());
        assertEquals("Default", configs.get(0).getName());
    }
}
