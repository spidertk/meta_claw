package meta.claw.core.config;

import meta.claw.core.config.loader.VesselConfigLoader;
import meta.claw.core.exception.VesselException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VesselConfigLoaderTest {

    @TempDir
    Path tempDir;

    private final VesselConfigLoader loader = new VesselConfigLoader();

    @Test
    void loadFromDirectory_returnsEmptyForMissingDir() {
        List<VesselConfig> result = loader.loadFromDirectory(tempDir.resolve("nonexistent"));
        assertTrue(result.isEmpty());
    }

    @Test
    void load_throwsWhenFileMissing() {
        assertThrows(VesselException.class, () -> loader.load(tempDir.resolve("nonexistent")));
    }

    @Test
    void load_parsesNestedYaml() throws Exception {
        String yaml = """
            identity:
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

        VesselConfig meta = loader.load(vesselDir);

        assertEquals("test-bot", meta.getIdentity().getId());
        assertEquals("Test Bot", meta.getIdentity().getName());
        assertEquals("ollama", meta.getLlm().getProvider());
        assertEquals(0.7, meta.getLlm().getOverrides().getTemperature());
        assertEquals("jsonl", meta.getMemory().getShortTermStore());
    }

    @Test
    void load_parsesMultiAgentConfig() throws Exception {
        String yaml = """
            identity:
              id: multi-bot
              name: Multi Bot
            agent_engine: alibaba
            agents:
              - name: planner
                description: Plan tasks
                model: gpt-4o-mini
                system_prompt: You are a planner.
                tools:
                  - search
              - name: coder
                description: Write code
                system_prompt: You are a coder.
            flow:
              mode: sequential
              routing_prompt: Choose one agent
              fallback_agent: planner
            """;
        Path vesselDir = tempDir.resolve("multi-agent-vessel");
        Files.createDirectories(vesselDir);
        Files.writeString(vesselDir.resolve("vessel.meta.yaml"), yaml);

        VesselConfig meta = loader.load(vesselDir);

        assertEquals("alibaba", meta.getAgentEngine());
        assertEquals(2, meta.getAgents().size());
        assertEquals("planner", meta.getAgents().get(0).getName());
        assertEquals("gpt-4o-mini", meta.getAgents().get(0).getModel());
        assertEquals(List.of("search"), meta.getAgents().get(0).getTools());
        assertEquals("coder", meta.getAgents().get(1).getName());
        assertEquals(AgentFlowMode.SEQUENTIAL, meta.getFlow().getModeEnum());
        assertEquals("planner", meta.getFlow().getFallbackAgent());
    }
}
