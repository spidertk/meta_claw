package meta.claw.cli;

import meta.claw.vessel.ProjectRootFinder;
import meta.claw.vessel.VesselTemplate;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 创建新的 Vessel。
 * <p>
 * 基于 vessel.tmpl.md 模板渲染生成 vessel.md，
 * 并创建完整的 Vessel 目录结构（skills/, knowledge/, conversations/, preferences/）。
 * </p>
 */
@Component
@Command(name = "create", description = "Create a new vessel")
public class CreateCommand implements Runnable {
    private final VesselTemplate vesselTemplate;

    public CreateCommand(VesselTemplate vesselTemplate) {
        this.vesselTemplate = vesselTemplate;
    }

    @Parameters(index = "0", description = "Vessel name")
    private String vesselName;

    @Option(names = {"--description", "-d"}, description = "Brief description of this vessel")
    private String description;

    @Override
    public void run() {
        Path vesselsDir = ProjectRootFinder.getMetaClawDir().resolve("vessels");

        if (vesselsDir.resolve(vesselName).toFile().exists()) {
            System.err.println("Vessel '" + vesselName + "' already exists!");
            return;
        }

        try {
            vesselTemplate.createVessel(vesselsDir, vesselName,
                    description != null ? description : "A customized AI vessel for specific tasks.");
            System.out.println("Created vessel: " + vesselName);
            System.out.println("Edit .meta-claw/vessels/" + vesselName + "/vessel.md to customize.");
            System.out.println("Run 'meta-claw chat " + vesselName + "' to start chatting.");
        } catch (Exception e) {
            System.err.println("Failed to create vessel: " + e.getMessage());
        }
    }
}
