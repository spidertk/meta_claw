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

    private static final String DEFAULT_CONFIG_YAML = """
            # Vessel 级模型配置（可选）
            # 此文件中非空字段会覆盖全局 config.yaml 中对应 provider 的配置
            # 留空或删除则表示完全使用全局配置
            provider: ""       # 指定使用全局 providers 下的哪个 provider，空则使用全局 default_provider
            model: ""          # 覆盖模型名称
            api_key: ""        # 覆盖 API Key
            base_url: ""       # 覆盖 Base URL
            temperature: ""    # 覆盖温度参数
            timeout: ""        # 覆盖超时（秒）
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

        Path profileFile = vesselDir.resolve("config.yaml");
        if (!Files.exists(profileFile)) {
            Files.writeString(profileFile, DEFAULT_CONFIG_YAML);
            log.info("已生成默认 Vessel 模型配置: {}", profileFile);
        }
    }
}
