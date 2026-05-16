package meta.claw.core.config;

import meta.claw.core.config.VesselConfig;
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
                provider: moonshot
                api_key: sk-test123
                base_url: https://api.moonshot.cn/v1
                temperature: 0.7
                timeout: 30.0
                memory:
                  short_term_store: jsonl
                  long_term_store: file
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
        assertEquals("moonshot", config.getProvider());
        assertEquals("sk-test123", config.getApiKey());
        assertEquals("https://api.moonshot.cn/v1", config.getBaseUrl());
        assertEquals(0.7, config.getTemperature());
        assertEquals(30.0, config.getTimeout());
        assertEquals("jsonl", config.getMemory().getShortTermStore());
        assertEquals("file", config.getMemory().getLongTermStore());
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
