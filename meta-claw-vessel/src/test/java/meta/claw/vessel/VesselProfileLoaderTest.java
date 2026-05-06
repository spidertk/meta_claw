package meta.claw.vessel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VesselProfileLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testLoadExistingConfigWithAllFields() throws Exception {
        Path vesselDir = tempDir.resolve("test-vessel");
        Files.createDirectories(vesselDir);
        String content = """
                provider: moonshot
                api_key: sk-test123
                base_url: https://api.moonshot.cn/v1
                model: kimi-k2.5
                temperature: 0.7
                timeout: 30.0
                """;
        Files.writeString(vesselDir.resolve("config.yaml"), content);

        VesselProfileLoader loader = new VesselProfileLoader();
        VesselProfileConfig config = loader.load(vesselDir);

        assertNotNull(config);
        assertEquals("moonshot", config.getProvider());
        assertEquals("sk-test123", config.getApiKey());
        assertEquals("https://api.moonshot.cn/v1", config.getBaseUrl());
        assertEquals("kimi-k2.5", config.getModel());
        assertEquals(0.7, config.getTemperature());
        assertEquals(30.0, config.getTimeout());
    }

    @Test
    void testLoadMissingConfigReturnsNull() {
        Path vesselDir = tempDir.resolve("missing-vessel");

        VesselProfileLoader loader = new VesselProfileLoader();
        VesselProfileConfig config = loader.load(vesselDir);

        assertNull(config);
    }

    @Test
    void testLoadPartialConfigOnlyModel() throws Exception {
        Path vesselDir = tempDir.resolve("partial-vessel");
        Files.createDirectories(vesselDir);
        String content = """
                model: kimi-k2.5
                """;
        Files.writeString(vesselDir.resolve("config.yaml"), content);

        VesselProfileLoader loader = new VesselProfileLoader();
        VesselProfileConfig config = loader.load(vesselDir);

        assertNotNull(config);
        assertNull(config.getProvider());
        assertNull(config.getApiKey());
        assertNull(config.getBaseUrl());
        assertEquals("kimi-k2.5", config.getModel());
        assertNull(config.getTemperature());
        assertNull(config.getTimeout());
    }
}
