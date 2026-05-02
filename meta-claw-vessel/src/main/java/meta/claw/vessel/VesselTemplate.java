package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class VesselTemplate {

    private static final String DEFAULT_VESSEL_MD = """
            ---
            id: default
            name: Default Vessel
            description: A general-purpose AI assistant
            emoji: 🤖
            model: kimi-k2.5
            system_prompt: |
              You are a helpful AI assistant. Answer user questions concisely and accurately.
            memory_enabled: true
            knowledge_dir: knowledge
            ---
            """;

    public void createDefaultVessel(Path baseDir) throws IOException {
        Path vesselDir = baseDir.resolve("default");
        Files.createDirectories(vesselDir);
        Files.createDirectories(vesselDir.resolve("skills"));
        Files.createDirectories(vesselDir.resolve("knowledge"));
        Files.createDirectories(vesselDir.resolve("memory"));

        Path configFile = vesselDir.resolve("vessel.md");
        Files.writeString(configFile, DEFAULT_VESSEL_MD);
        log.info("已生成默认 Vessel 配置: {}", configFile);
    }
}
