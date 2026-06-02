package meta.claw.core.config;

import meta.claw.core.exception.VesselException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VesselMetaLoaderTest {

    @TempDir
    Path tempDir;

    private final VesselMetaLoader loader = new VesselMetaLoader();

    @Test
    void loadFromDirectory_returnsEmptyForMissingDir() {
        List<VesselMeta> result = loader.loadFromDirectory(tempDir.resolve("nonexistent"));
        assertTrue(result.isEmpty());
    }

    @Test
    void load_throwsWhenFileMissing() {
        assertThrows(VesselException.class, () -> loader.load(tempDir.resolve("nonexistent")));
    }

    @Test
    void load_parsesNestedYaml() throws Exception {
        String yaml = """
            meta:
              id: test-bot
              name: Test Bot
              description: A test vessel
            llm:
              provider: ollama
              model: llama3
              overrides:
                temperature: 0.7
            memory:
              short_term_store: jsonl
            """;
        Path vesselDir = tempDir.resolve("test-vessel");
        Files.createDirectories(vesselDir);
        Files.writeString(vesselDir.resolve("vessel.meta.yaml"), yaml);

        VesselMeta meta = loader.load(vesselDir);

        assertEquals("test-bot", meta.getMeta().getId());
        assertEquals("Test Bot", meta.getMeta().getName());
        assertEquals("ollama", meta.getLlm().getProvider());
        assertEquals(0.7, meta.getLlm().getOverrides().getTemperature());
        assertEquals("jsonl", meta.getMemory().getShortTermStore());
    }
}
