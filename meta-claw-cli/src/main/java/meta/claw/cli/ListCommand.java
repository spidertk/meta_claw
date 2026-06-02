package meta.claw.cli;

import meta.claw.core.infra.ProjectRootFinder;
import meta.claw.core.config.VesselMeta;
import meta.claw.core.config.VesselMetaLoader;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * 列出所有已创建的 Vessel。
 * <p>
 * 扫描 .meta-claw/vessels/ 目录，读取每个子目录下的 vessel.meta.yaml，
 * 以表格形式展示 Vessel 的完整信息。
 * </p>
 */
@Component
@Command(name = "list", description = "List all vessels")
public class ListCommand implements Runnable {

    private final VesselMetaLoader metaLoader;

    public ListCommand(VesselMetaLoader metaLoader) {
        this.metaLoader = metaLoader;
    }

    @Option(names = {"--all", "-a"}, description = "Include hidden vessels")
    private boolean includeHidden;

    @Override
    public void run() {
        Path vesselsDir = ProjectRootFinder.getMetaClawDir().resolve("vessels");

        if (!Files.exists(vesselsDir) || !Files.isDirectory(vesselsDir)) {
            System.err.println("Vessels directory not found. Run 'meta-claw init' first.");
            return;
        }

        List<Path> vesselDirs;
        try (Stream<Path> paths = Files.list(vesselsDir)) {
            vesselDirs = paths.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            System.err.println("Failed to scan vessels directory: " + e.getMessage());
            return;
        }

        if (vesselDirs.isEmpty()) {
            System.out.println("No vessels found. Run 'meta-claw init' to create the default vessel.");
            return;
        }

        // 表头
        System.out.println("┌──────────────┬──────────────┬──────────────────────────────┬──────┬────────────────────┬────────┬───────────┬──────────┐");
        System.out.println(String.format("│ %-12s │ %-12s │ %-28s │ %-4s │ %-18s │ %-6s │ %-9s │ %-8s │",
                "ID", "Name", "Description", "Emoji", "Model", "Role", "AutoServe", "Provider"));
        System.out.println("├──────────────┼──────────────┼──────────────────────────────┼──────┼────────────────────┼────────┼───────────┼──────────┤");

        for (Path dir : vesselDirs) {
            String id = dir.getFileName().toString();
            if (id.startsWith(".") && !includeHidden) {
                continue;
            }

            VesselMeta meta;
            try {
                meta = metaLoader.load(dir);
            } catch (Exception e) {
                // 加载失败时显示基本信息
                System.out.println(String.format("│ %-12s │ %-12s │ %-28s │ %-4s │ %-18s │ %-6s │ %-9s │ %-8s │",
                        truncate(id, 12), "(error)", truncate(e.getMessage(), 28), "", "", "", "", ""));
                continue;
            }

            VesselMeta.MetaInfo m = meta != null ? meta.getMeta() : null;
            String name = m != null && m.getName() != null ? m.getName()
                    : (m != null && m.getDisplayName() != null ? m.getDisplayName() : id);
            String desc = m != null && m.getDescription() != null ? m.getDescription() : "";
            String emoji = m != null && m.getEmoji() != null ? m.getEmoji() : "";

            VesselMeta.LlmConfig llm = meta != null ? meta.getLlm() : null;
            String model = llm != null && llm.getModel() != null ? llm.getModel() : "";
            String provider = llm != null && llm.getProvider() != null ? llm.getProvider() : "";

            VesselMeta.RuntimeConfig rt = meta != null ? meta.getRuntime() : null;
            String role = rt != null && rt.getRole() != null ? rt.getRole() : "";
            String autoServe = rt != null && rt.isAutoServe() ? "true" : "false";

            // Truncate for display
            id = truncate(id, 12);
            name = truncate(name, 12);
            desc = truncate(desc, 28);
            emoji = truncate(emoji, 4);
            model = truncate(model, 18);
            role = truncate(role, 6);
            provider = truncate(provider, 8);

            System.out.println(String.format("│ %-12s │ %-12s │ %-28s │ %-4s │ %-18s │ %-6s │ %-9s │ %-8s │",
                    id, name, desc, emoji, model, role, autoServe, provider));
        }
        System.out.println("└──────────────┴──────────────┴──────────────────────────────┴──────┴────────────────────┴────────┴───────────┴──────────┘");
        System.out.println("\nUse 'meta-claw chat <id>' to start chatting.");
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }
}
