package meta.claw.cli;

import meta.claw.vessel.ProjectRootFinder;
import meta.claw.vessel.VesselTemplate;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Command(name = "init", description = "Initialize Meta-Claw config directory and default vessel")
public class InitCommand implements Runnable {

    private static final String CONFIG_YAML = """
            # 是否开启 DEBUG 级别日志，仅在设置为 true 时 meta.claw 包才会输出 debug 日志
            log.debug: false
            default_provider: moonshot
            providers:
              moonshot:
                api_key: "your-api-key"
                base_url: "https://api.moonshot.cn/"
                model: "kimi-k2.5"
                temperature: 1
                timeout: 60.0
            """;

    @Override
    public void run() {
        try {
            Path baseDir = ProjectRootFinder.getMetaClawDir();
            Files.createDirectories(baseDir);

            // Create skills directory
            Files.createDirectories(baseDir.resolve("skills"));

            // Create default vessel
            VesselTemplate template = new VesselTemplate();
            template.createDefaultVessel(baseDir.resolve("vessels"));

            // Create config.yaml if not exists
            Path configFile = baseDir.resolve("config.yaml");
            if (!Files.exists(configFile)) {
                Files.writeString(configFile, CONFIG_YAML);
            }

            System.out.println("Meta-Claw initialized at: " + baseDir);
            System.out.println("Please edit .meta-claw/config.yaml and set your API key.");
            System.out.println("Run 'meta-claw chat default' to start chatting.");
        } catch (Exception e) {
            throw new RuntimeException("Init failed: " + e.getMessage(), e);
        }
    }
}
