package meta.claw.cli;

import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.runtime.VesselManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 初始化 Meta-Claw 配置目录和 default vessel。
 * <p>
 * 1. 创建 .meta-claw/ 目录结构（skills/）
 * 2. 通过 {@link VesselManager#createVessel} 创建 default vessel（写盘 + 内存 + runtime 一体化）
 * 3. 写全局 config.yaml（从模板）
 * </p>
 */
@Component
@Command(name = "init", description = "Initialize Meta-Claw config directory and default vessel")
public class InitCommand implements Runnable {

    private static final String GLOBAL_CONFIG_TEMPLATE = "/templates/global-config.tmpl.yaml";
    private static final String DEFAULT_VESSEL_DESC = "A general-purpose AI assistant";

    @Autowired
    private VesselManager vesselManager;

    @Override
    public void run() {
        try {
            Path baseDir = ProjectRootFinder.getMetaClawDir();
            Files.createDirectories(baseDir);

            // Create skills directory
            Files.createDirectories(baseDir.resolve("skills"));

            // Create default vessel through VesselManager
            if (vesselManager.hasVessel("default")) {
                System.out.println("Default vessel already exists. Skipping vessel creation.");
            } else {
                vesselManager.createVessel("default", DEFAULT_VESSEL_DESC);
            }

            // Create config.yaml from template if not exists
            Path configFile = baseDir.resolve("config.yaml");
            if (!Files.exists(configFile)) {
                String globalConfig = loadGlobalConfigTemplate();
                Files.writeString(configFile, globalConfig);
            }

            System.out.println("Meta-Claw initialized at: " + baseDir);
            System.out.println("Please edit .meta-claw/config.yaml and set your API key.");
            System.out.println("Run 'meta-claw chat default' to start chatting.");
        } catch (Exception e) {
            throw new RuntimeException("Init failed: " + e.getMessage(), e);
        }
    }

    private String loadGlobalConfigTemplate() {
        try (InputStream is = getClass().getResourceAsStream(GLOBAL_CONFIG_TEMPLATE)) {
            if (is == null) {
                throw new IllegalStateException("classpath 中未找到模板: " + GLOBAL_CONFIG_TEMPLATE);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载全局配置模板失败", e);
        }
    }
}
