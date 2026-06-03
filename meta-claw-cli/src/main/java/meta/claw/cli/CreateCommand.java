package meta.claw.cli;

import meta.claw.core.runtime.VesselManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * 创建新的 Vessel。
 * <p>
 * 通过 {@link VesselManager} 统一创建：写盘、加载配置、注册内存、注册 Runtime。
 * </p>
 */
@Component
@Command(name = "create", description = "Create a new vessel")
public class CreateCommand implements Runnable {

    @Autowired
    private VesselManager vesselManager;

    @Parameters(index = "0", description = "Vessel name")
    private String vesselName;

    @Option(names = {"--description", "-d"}, description = "Brief description of this vessel")
    private String description;

    @Override
    public void run() {
        if (vesselManager.hasVessel(vesselName)) {
            System.err.println("Vessel '" + vesselName + "' already exists!");
            return;
        }

        try {
            vesselManager.createVessel(vesselName, description);
            System.out.println("Created vessel: " + vesselName);
            System.out.println("Edit .meta-claw/vessels/" + vesselName + "/vessel.profile.md to customize.");
            System.out.println("Run 'meta-claw chat " + vesselName + "' to start chatting.");
        } catch (Exception e) {
            System.err.println("Failed to create vessel: " + e.getMessage());
        }
    }
}
