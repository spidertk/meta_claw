package meta.claw.cli;

import meta.claw.export.ExpertTemplate;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Command(name = "init", description = "Initialize Meta-Claw config directory and default expert")
public class InitCommand implements Runnable {

    @Override
    public void run() {
        try {
            Path baseDir = Paths.get(System.getProperty("user.home"), ".meta-claw");
            Files.createDirectories(baseDir);
            Files.createDirectories(baseDir.resolve("experts"));

            ExpertTemplate template = new ExpertTemplate();
            template.createDefaultExpert(baseDir.resolve("experts"));

            System.out.println("Meta-Claw initialized at: " + baseDir);
            System.out.println("Run 'meta-claw chat default' to start chatting.");
        } catch (Exception e) {
            System.err.println("Init failed: " + e.getMessage());
        }
    }
}
