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
    void testLoadSingle() throws Exception {
        Path vesselDir = tempDir.resolve("default");
        Files.createDirectories(vesselDir);
        String content = """
                ---
                id: test-vessel
                name: Test Vessel
                model: kimi-k2.5
                system_prompt: You are a test assistant.
                preferences_enabled: true
                ---
                """;
        Files.writeString(vesselDir.resolve("vessel.md"), content);

        VesselConfigLoader loader = new VesselConfigLoader();
        VesselConfig config = loader.loadSingle(vesselDir.resolve("vessel.md"));

        assertNotNull(config);
        assertEquals("test-vessel", config.getId());
        assertEquals("Test Vessel", config.getName());
        assertEquals("kimi-k2.5", config.getModel());
        assertTrue(config.isPreferencesEnabled());
    }

    @Test
    void testLoadFromDirectory() throws Exception {
        Path vesselDir = tempDir.resolve("default");
        Files.createDirectories(vesselDir);
        Files.writeString(vesselDir.resolve("vessel.md"), """
                ---
                id: default
                name: Default
                ---
                """);

        VesselConfigLoader loader = new VesselConfigLoader();
        List<VesselConfig> configs = loader.loadFromDirectory(tempDir);

        assertEquals(1, configs.size());
        assertEquals("default", configs.get(0).getId());
    }
}
