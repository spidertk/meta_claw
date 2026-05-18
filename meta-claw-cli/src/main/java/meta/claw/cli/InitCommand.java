package meta.claw.cli;

import meta.claw.vessel.ProjectRootFinder;
import meta.claw.vessel.VesselTemplate;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Command(name = "init", description = "Initialize Meta-Claw config directory and default vessel")
public class InitCommand implements Runnable {

    private static final String GLOBAL_CONFIG_TEMPLATE = "/templates/global-config.tmpl.yaml";
    private final VesselTemplate vesselTemplate;

    public InitCommand(VesselTemplate vesselTemplate) {
        this.vesselTemplate = vesselTemplate;
    }

    @Override
    public void run() {
        try {
            Path baseDir = ProjectRootFinder.getMetaClawDir();
            Files.createDirectories(baseDir);

            // Create skills directory
            Files.createDirectories(baseDir.resolve("skills"));

            // Create default vessel
            vesselTemplate.createDefaultVessel(baseDir.resolve("vessels"));

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
