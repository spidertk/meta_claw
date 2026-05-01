package meta.claw.export;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class ExpertTemplate {

    private static final String DEFAULT_CONTENT = """
            id: default
            name: Default Expert
            description: A general-purpose AI assistant
            emoji: 🤖
            model: moonshot-v1-8k
            systemPrompt: |
              You are a helpful AI assistant. Answer user questions concisely and accurately.
            memoryEnabled: true
            knowledgeDir: knowledge
            """;

    public void createDefaultExpert(Path baseDir) throws IOException {
        Path expertDir = baseDir.resolve("default");
        Files.createDirectories(expertDir);
        Path configFile = expertDir.resolve("expert.yaml");
        Files.writeString(configFile, DEFAULT_CONTENT);
        log.info("已生成默认专家配置: {}", configFile);
    }
}
