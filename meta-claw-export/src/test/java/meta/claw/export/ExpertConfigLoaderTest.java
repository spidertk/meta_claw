package meta.claw.export;

import meta.claw.core.model.ExpertConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExpertConfigLoaderTest {

    @Test
    void testLoadFromDirectory(@TempDir Path tempDir) throws Exception {
        Path expertDir = tempDir.resolve("test-expert");
        Files.createDirectories(expertDir);
        Files.writeString(expertDir.resolve("expert.yaml"), """
                id: test-expert
                name: Test Expert
                model: gpt-4
                systemPrompt: You are a test assistant.
                memoryEnabled: true
                """);

        ExpertConfigLoader loader = new ExpertConfigLoader();
        List<ExpertConfig> configs = loader.loadFromDirectory(tempDir);

        assertEquals(1, configs.size());
        assertEquals("test-expert", configs.get(0).getId());
        assertEquals("Test Expert", configs.get(0).getName());
        assertEquals("gpt-4", configs.get(0).getModel());
    }
}
