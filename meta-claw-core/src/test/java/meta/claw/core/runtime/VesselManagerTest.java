package meta.claw.core.runtime;

import meta.claw.core.model.VesselConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VesselManagerTest {

    @TempDir
    Path tempDir;

    private Path createVesselDir(String vesselName, String configContent, String vesselMdContent) throws IOException {
        Path vesselDir = tempDir.resolve(vesselName);
        Files.createDirectories(vesselDir);
        Files.writeString(vesselDir.resolve("config.yaml"), configContent);
        if (vesselMdContent != null) {
            Files.writeString(vesselDir.resolve("vessel.md"), vesselMdContent);
        }
        return vesselDir;
    }

    @Test
    void loadVessels_shouldLoadFromConfigYamlAndVesselMd() throws IOException {
        String configYaml = """
            id: test-vessel
            name: Test Vessel
            description: A test vessel
            emoji: 🤖
            model: gpt-4
            system_prompt: You are a test assistant
            preferences_enabled: true
            role: member
            auto_serve: false
            exclude_tools: []
            """;
        String vesselMd = """
            # Test Vessel

            ## Identity

            Test identity content

            ## Soul

            Test soul content

            ## Capabilities

            Test capabilities content
            """;
        createVesselDir("test-vessel", configYaml, vesselMd);

        VesselManager manager = new VesselManager(tempDir.toString());
        manager.loadVessels();

        assertTrue(manager.hasVessel("test-vessel"));
        VesselConfig config = manager.getConfig("test-vessel");
        assertNotNull(config);
        assertEquals("test-vessel", config.getId());
        assertEquals("Test Vessel", config.getName());
        assertEquals("You are a test assistant", config.getSystemPrompt());
        assertEquals("member", config.getRole());
        assertTrue(config.isPreferencesEnabled());
        assertEquals("Test identity content", config.getIdentity());
        assertEquals("Test soul content", config.getSoul());
        assertEquals("Test capabilities content", config.getCapabilities());
    }

    @Test
    void loadVessels_shouldHandleMissingDirectory() {
        VesselManager manager = new VesselManager("/nonexistent/path");
        assertDoesNotThrow(manager::loadVessels);
        assertTrue(manager.listAvailableVessels().isEmpty());
    }

    @Test
    void loadVessels_shouldSkipConfigWithoutId() throws IOException {
        String configYaml = """
            name: No-Id Vessel
            description: Missing id field
            """;
        createVesselDir("no-id-vessel", configYaml, null);

        VesselManager manager = new VesselManager(tempDir.toString());
        manager.loadVessels();

        assertFalse(manager.hasVessel("no-id-vessel"));
        assertTrue(manager.listAvailableVessels().isEmpty());
    }

    @Test
    void runtimeRegistration_shouldWork() throws IOException {
        String configYaml = """
            id: runtime-test
            name: Runtime Test
            """;
        createVesselDir("runtime-test", configYaml, null);

        VesselManager manager = new VesselManager(tempDir.toString());
        manager.loadVessels();

        assertEquals(1, manager.listAvailableVessels().size());
        assertNotNull(manager.getConfig("runtime-test"));

        VesselRuntime mockRuntime = new VesselRuntime(manager.getConfig("runtime-test"), null);
        manager.registerRuntime("runtime-test", mockRuntime);

        assertNotNull(manager.getRuntime("runtime-test"));
        assertEquals(mockRuntime, manager.getRuntime("runtime-test"));
    }
}
