package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
public class VesselTemplate {

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

    /**
     * 基于 classpath 模板创建默认 Vessel
     */
    public void createDefaultVessel(Path baseDir) throws IOException {
        createVessel(baseDir, "default", "A general-purpose AI assistant");
    }

    /**
     * 基于 vessel.tmpl.md 模板创建新 Vessel
     *
     * @param vesselsDir   vessels 根目录
     * @param name         Vessel 名称（即目录名）
     * @param description  Vessel 描述
     */
    public void createVessel(Path vesselsDir, String name, String description) throws IOException {
        Path vesselDir = vesselsDir.resolve(name);
        Files.createDirectories(vesselDir);
        Files.createDirectories(vesselDir.resolve("skills"));
        Files.createDirectories(vesselDir.resolve("knowledge"));
        Files.createDirectories(vesselDir.resolve("conversations"));
        Files.createDirectories(vesselDir.resolve("preferences"));

        // 加载模板
        String template = loadTemplate();
        String rendered = renderTemplate(template, Map.of(
                "name", name,
                "created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "description", description != null ? description : ""
        ));

        Path vesselMd = vesselDir.resolve("vessel.md");
        Files.writeString(vesselMd, rendered);
        log.info("已生成 Vessel 配置: {}", vesselMd);

        Path profileFile = vesselDir.resolve("config.yaml");
        if (!Files.exists(profileFile)) {
            Files.writeString(profileFile, DEFAULT_CONFIG_YAML);
            log.info("已生成 Vessel 模型配置: {}", profileFile);
        }
    }

    private String loadTemplate() {
        try (var is = getClass().getResourceAsStream("/templates/vessel.tmpl.md")) {
            if (is == null) {
                log.warn("classpath 中未找到 templates/vessel.tmpl.md，使用默认模板");
                return defaultTemplate();
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("加载 vessel.tmpl.md 失败，使用默认模板: {}", e.getMessage());
            return defaultTemplate();
        }
    }

    private String renderTemplate(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String defaultTemplate() {
        return """
                ---
                id: {name}
                name: {name}
                description: {description}
                emoji: 🤖
                model: ""
                system_prompt: |
                  You are a helpful AI assistant.
                preferences_enabled: true
                knowledge_dir: knowledge
                role: member
                auto_serve: false
                exclude_tools: []
                ---

                ## Identity

                {description}

                ## Soul

                <!-- 在此定义 Vessel 的性格、风格、语气等人格特质 -->

                ## Domain Knowledge

                <!-- 在此添加领域知识 -->

                ## Capabilities

                <!-- 在此添加能力说明 -->

                ## Guidelines

                <!-- 在此添加指导原则 -->

                ## Preferences

                <!-- 在此添加偏好设置 -->
                """;
    }
}
