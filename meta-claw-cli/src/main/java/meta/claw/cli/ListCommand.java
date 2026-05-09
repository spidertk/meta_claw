package meta.claw.cli;

import meta.claw.core.model.VesselConfig;
import meta.claw.vessel.ProjectRootFinder;
import meta.claw.vessel.VesselConfigLoader;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.List;

/**
 * 列出所有已创建的 Vessel。
 * <p>
 * 扫描 .meta-claw/vessels/ 目录，读取每个子目录下的 config.yaml 和 vessel.md，
 * 以表格形式展示 Vessel 的完整信息。
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

        // 表头
        System.out.println("┌──────────────┬──────────────┬──────────────────────────────┬──────┬────────────────────┬────────┬───────────┬─────────────┐");
        System.out.println(String.format("│ %-12s │ %-12s │ %-28s │ %-4s │ %-18s │ %-6s │ %-9s │ %-11s │",
                "ID", "Name", "Description", "Emoji", "Model", "Role", "AutoServe", "Preferences"));
        System.out.println("├──────────────┼──────────────┼──────────────────────────────┼──────┼────────────────────┼────────┼───────────┼─────────────┤");

        for (VesselConfig vessel : vessels) {
            String id = vessel.getId() != null ? vessel.getId() : "N/A";
            if (id.startsWith(".") && !includeHidden) {
                continue;
            }
            String name = vessel.getName() != null ? vessel.getName() : id;
            String desc = vessel.getDescription() != null ? vessel.getDescription() : "";
            String emoji = vessel.getEmoji() != null ? vessel.getEmoji() : "";
            String model = vessel.getModel() != null ? vessel.getModel() : "";
            String role = vessel.getRole() != null ? vessel.getRole() : "";
            String autoServe = vessel.isAutoServe() ? "true" : "false";
            String preferences = vessel.isPreferencesEnabled() ? "true" : "false";

            // Truncate for display
            id = id.length() > 12 ? id.substring(0, 9) + "..." : id;
            name = name.length() > 12 ? name.substring(0, 9) + "..." : name;
            desc = desc.length() > 28 ? desc.substring(0, 25) + "..." : desc;
            emoji = emoji.length() > 4 ? emoji.substring(0, 3) : emoji;
            model = model.length() > 18 ? model.substring(0, 15) + "..." : model;
            role = role.length() > 6 ? role.substring(0, 5) : role;

            System.out.println(String.format("│ %-12s │ %-12s │ %-28s │ %-4s │ %-18s │ %-6s │ %-9s │ %-11s │",
                    id, name, desc, emoji, model, role, autoServe, preferences));
        }
        System.out.println("└──────────────┴──────────────┴──────────────────────────────┴──────┴────────────────────┴────────┴───────────┴─────────────┘");
        System.out.println("\nUse 'meta-claw chat <id>' to start chatting.");
    }
}
