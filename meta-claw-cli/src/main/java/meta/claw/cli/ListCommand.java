package meta.claw.cli;

import meta.claw.core.model.VesselConfig;
import meta.claw.vessel.ProjectRootFinder;
import meta.claw.vessel.VesselConfigLoader;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 列出所有已创建的 Vessel。
 * <p>
 * 扫描 .meta-claw/vessels/ 目录，读取每个子目录下的 vessel.md，
 * 以表格形式展示 Vessel 的基本信息。
 * </p>
 */
@Component
@Command(name = "list", description = "List all vessels")
public class ListCommand implements Runnable {

    @Option(names = {"--all", "-a"}, description = "Include hidden vessels")
    private boolean includeHidden;

    @Override
    public void run() {
        Path vesselsDir = ProjectRootFinder.getMetaClawDir().resolve("vessels");

        if (!vesselsDir.toFile().exists()) {
            System.err.println("Vessels directory not found. Run 'meta-claw init' first.");
            return;
        }

        VesselConfigLoader loader = new VesselConfigLoader();
        List<VesselConfig> vessels = loader.loadFromDirectory(vesselsDir);

        if (vessels.isEmpty()) {
            System.out.println("No vessels found. Run 'meta-claw init' to create the default vessel.");
            return;
        }

        System.out.println("┌──────────────────────────────┬──────────────────────────────┬────────────────────┐");
        System.out.println(String.format("│ %-28s │ %-28s │ %-18s │", "Name", "Description", "Model"));
        System.out.println("├──────────────────────────────┼──────────────────────────────┼────────────────────┤");

        for (VesselConfig vessel : vessels) {
            String id = vessel.getId() != null ? vessel.getId() : "N/A";
            if (id.startsWith(".") && !includeHidden) {
                continue;
            }
            String name = vessel.getName() != null ? vessel.getName() : id;
            String desc = vessel.getDescription() != null ? vessel.getDescription() : "";
            String model = vessel.getModel() != null ? vessel.getModel() : "";

            // Truncate for display
            name = name.length() > 28 ? name.substring(0, 25) + "..." : name;
            desc = desc.length() > 28 ? desc.substring(0, 25) + "..." : desc;
            model = model.length() > 18 ? model.substring(0, 15) + "..." : model;

            System.out.println(String.format("│ %-28s │ %-28s │ %-18s │", name, desc, model));
        }
        System.out.println("└──────────────────────────────┴──────────────────────────────┴────────────────────┘");
        System.out.println("\nUse 'meta-claw chat <id>' to start chatting.");
    }
}
