package meta.claw.vessel;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
public class VesselTemplate {

    private static final String VESSEL_CONFIG_TEMPLATE = "/templates/vessel-config.tmpl.yaml";
    private static final String VESSEL_BODY_TEMPLATE = "/templates/vessel.tmpl.md";

    /**
     * 基于 classpath 模板创建默认 Vessel
     */
    public void createDefaultVessel(Path vesselsDir) throws IOException {
        createVessel(vesselsDir, "default", "A general-purpose AI assistant");
    }

    /**
     * 基于模板创建新 Vessel
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

        Map<String, String> vars = Map.of(
                "name", name,
                "created_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
                "description", description != null ? description : ""
        );

        // 1. 渲染并写入 config.yaml
        String configTemplate = loadTemplate(VESSEL_CONFIG_TEMPLATE);
        String configRendered = renderTemplate(configTemplate, vars);
        Path configFile = vesselDir.resolve("config.yaml");
        Files.writeString(configFile, configRendered);
        log.info("已生成 Vessel 配置: {}", configFile);

        // 2. 渲染并写入 vessel.md
        String bodyTemplate = loadTemplate(VESSEL_BODY_TEMPLATE);
        String bodyRendered = renderTemplate(bodyTemplate, vars);
        Path vesselMd = vesselDir.resolve("vessel.md");
        Files.writeString(vesselMd, bodyRendered);
        log.info("已生成 Vessel 模板: {}", vesselMd);
    }

    private String loadTemplate(String resourcePath) {
        try (var is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("classpath 中未找到模板: " + resourcePath);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载模板失败: " + resourcePath, e);
        }
    }

    private String renderTemplate(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
