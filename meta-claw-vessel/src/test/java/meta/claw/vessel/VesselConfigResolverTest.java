package meta.claw.vessel;

import meta.claw.core.model.VesselConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VesselConfigResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void testResolveWithVesselOverride() throws Exception {
        // 全局配置
        String globalYaml = """
                default_provider: moonshot
                providers:
                  moonshot:
                    api_key: global-key
                    base_url: https://global.com
                    model: global-model
                    temperature: 1.0
                    timeout: 60.0
                """;
        Files.writeString(tempDir.resolve("config.yaml"), globalYaml);

        // Vessel 目录
        Path vesselDir = tempDir.resolve("vessels").resolve("default");
        Files.createDirectories(vesselDir);

        String vesselMd = """
                ---
                id: default
                name: Default Vessel
                model: vessel-md-model
                system_prompt: You are a test assistant.
                ---
                """;
        Files.writeString(vesselDir.resolve("vessel.md"), vesselMd);

        String vesselYaml = """
                provider: moonshot
                model: override-model
                temperature: 0.5
                """;
        Files.writeString(vesselDir.resolve("config.yaml"), vesselYaml);

        VesselConfigResolver resolver = new VesselConfigResolver();
        ResolvedVesselConfig resolved = resolver.resolve(tempDir, "default");

        assertEquals("moonshot", resolved.getProviderName());
        assertEquals("global-key", resolved.getProviderConfig().getApiKey());
        assertEquals("https://global.com", resolved.getProviderConfig().getBaseUrl());
        assertEquals("override-model", resolved.getProviderConfig().getModel());
        assertEquals(0.5, resolved.getProviderConfig().getTemperature());
        assertEquals(60.0, resolved.getProviderConfig().getTimeout());
        assertNotNull(resolved.getVesselConfig());
        assertEquals("default", resolved.getVesselConfig().getId());
    }

    @Test
    void testResolveFallbackToGlobal() throws Exception {
        String globalYaml = """
                default_provider: moonshot
                providers:
                  moonshot:
                    api_key: global-key
                    base_url: https://global.com
                    model: global-model
                """;
        Files.writeString(tempDir.resolve("config.yaml"), globalYaml);

        Path vesselDir = tempDir.resolve("vessels").resolve("default");
        Files.createDirectories(vesselDir);

        String vesselMd = """
                ---
                id: default
                name: Default Vessel
                ---
                """;
        Files.writeString(vesselDir.resolve("vessel.md"), vesselMd);

        VesselConfigResolver resolver = new VesselConfigResolver();
        ResolvedVesselConfig resolved = resolver.resolve(tempDir, "default");

        assertEquals("moonshot", resolved.getProviderName());
        assertEquals("global-model", resolved.getProviderConfig().getModel());
        assertEquals("global-key", resolved.getProviderConfig().getApiKey());
    }

    @Test
    void testResolveVesselMdModelFallback() throws Exception {
        String globalYaml = """
                default_provider: moonshot
                providers:
                  moonshot:
                    api_key: global-key
                    model: ""
                """;
        Files.writeString(tempDir.resolve("config.yaml"), globalYaml);

        Path vesselDir = tempDir.resolve("vessels").resolve("default");
        Files.createDirectories(vesselDir);

        String vesselMd = """
                ---
                id: default
                model: vessel-model
                ---
                """;
        Files.writeString(vesselDir.resolve("vessel.md"), vesselMd);

        VesselConfigResolver resolver = new VesselConfigResolver();
        ResolvedVesselConfig resolved = resolver.resolve(tempDir, "default");

        assertEquals("vessel-model", resolved.getProviderConfig().getModel());
    }
}
